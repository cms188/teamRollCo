package com.example.recipe_pocket.repository

import android.net.Uri
import android.util.Log
import com.example.recipe_pocket.ai.AITagGenerator
import com.example.recipe_pocket.data.RecipeData
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.util.UUID

object RecipeSavePipeline {

    private const val TAG = "RecipeSavePipeline"
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    suspend fun saveRecipeWithAi(recipe: RecipeData) {
        val user = auth.currentUser ?: throw IllegalStateException("로그인이 필요합니다.")
        val tags = generateTags(recipe)
        val thumbnailUrl = uploadImage(recipe.thumbnailUrl)
        val stepImageUrls = recipe.steps.map { uploadImage(it.imageUri) }

        val firestoreMap = buildFirestoreMap(
            recipe = recipe,
            tags = tags,
            thumbnailUrl = thumbnailUrl,
            stepImageUrls = stepImageUrls,
            userId = user.uid,
            userEmail = user.email.orEmpty()
        )

        db.collection("Recipes").add(firestoreMap).await()

        val userRef = db.collection("Users").document(user.uid)
        val unlockedTitle = updateRecipeStats(userRef)
        if (!unlockedTitle.isNullOrBlank()) {
            runCatching { NotificationHandler.createTitleNotification(user.uid, unlockedTitle) }
                .onFailure { Log.w(TAG, "칭호 알림 생성 실패", it) }
        }
    }

    suspend fun saveDraft(recipe: RecipeData) {
        val user = auth.currentUser ?: throw IllegalStateException("로그인이 필요합니다.")
        val thumbnailUrl = uploadImage(recipe.thumbnailUrl)
        val stepImageUrls = recipe.steps.map { uploadImage(it.imageUri) }

        val firestoreMap = buildFirestoreMap(
            recipe = recipe,
            tags = emptySet(),
            thumbnailUrl = thumbnailUrl,
            stepImageUrls = stepImageUrls,
            userId = user.uid,
            userEmail = user.email.orEmpty()
        )

        db.collection("TempSaves").add(firestoreMap).await()
    }

    private suspend fun generateTags(recipe: RecipeData): Set<String> {
        val aiInput = buildAiInput(recipe)
        return AITagGenerator.generateTags(aiInput)
            .onFailure { Log.e(TAG, "AI 태그 생성 실패", it) }
            .getOrElse { emptySet() }
    }

    private fun buildAiInput(recipe: RecipeData): String {
        val ingredients = recipe.ingredients.joinToString(", ") { it.name.orEmpty() }
        return """
            - 레시피 제목: ${recipe.title}
            - 간단한 설명: ${recipe.description}
            - 주요 재료: $ingredients
        """.trimIndent()
    }

    private suspend fun uploadImage(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val uri = Uri.parse(uriString)
        val storageRef = storage.reference.child("recipe_images/${UUID.randomUUID()}")
        storageRef.putFile(uri).await()
        return storageRef.downloadUrl.await().toString()
    }

    private fun buildFirestoreMap(
        recipe: RecipeData,
        tags: Set<String>,
        thumbnailUrl: String?,
        stepImageUrls: List<String?>,
        userId: String,
        userEmail: String
    ): Map<String, Any?> {
        return hashMapOf(
            "userId" to userId,
            "userEmail" to userEmail,
            "title" to recipe.title,
            "simpleDescription" to recipe.description,
            "thumbnailUrl" to (thumbnailUrl ?: ""),
            "servings" to recipe.servings,
            "category" to recipe.category,
            "difficulty" to recipe.difficulty,
            "cookingTime" to recipe.cookingTimeMinutes,
            "ingredients" to recipe.ingredients.map { ingredient ->
                mapOf(
                    "name" to (ingredient.name ?: ""),
                    "amount" to (ingredient.amount ?: ""),
                    "unit" to (ingredient.unit ?: "")
                )
            },
            "tools" to recipe.tools,
            "steps" to recipe.steps.mapIndexed { index, step ->
                mapOf(
                    "stepNumber" to index + 1,
                    "title" to step.stepTitle,
                    "description" to step.stepDescription,
                    "imageUrl" to (stepImageUrls.getOrNull(index) ?: ""),
                    "time" to step.timerSeconds,
                    "useTimer" to step.useTimer
                )
            },
            "tags" to tags.toList(),
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )
    }

    private suspend fun updateRecipeStats(userRef: DocumentReference): String? {
        return try {
            userRef.update("recipeCount", FieldValue.increment(1)).await()
            val snapshot = userRef.get().await()
            if (!snapshot.exists()) return null

            val currentCount = snapshot.getLong("recipeCount") ?: 0L
            val newTitle = when (currentCount) {
                3L -> "새싹 요리사"
                10L -> "우리집 요리사"
                25L -> "요리 장인"
                50L -> "레시피 마스터"
                else -> null
            }

            if (newTitle != null) {
                userRef.update("unlockedTitles", FieldValue.arrayUnion(newTitle)).await()
            }
            newTitle
        } catch (e: Exception) {
            Log.w(TAG, "사용자 레시피 통계 업데이트 실패", e)
            null
        }
    }
}
