package com.example.recipe_pocket.repository

import android.net.Uri
import android.util.Log
import com.example.recipe_pocket.ai.AITagGenerator
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.data.RecipeStep_write
import com.example.recipe_pocket.data.TempSaveDraft
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
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

    suspend fun fetchTempSaves(): List<TempSaveDraft> {
        val user = auth.currentUser ?: throw IllegalStateException("로그인이 필요합니다.")
        val snapshot = db.collection("TempSaves")
            .whereEqualTo("userId", user.uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            mapTempSaveDocument(doc)?.let { recipe ->
                TempSaveDraft(
                    id = doc.id,
                    recipe = recipe,
                    updatedAt = doc.getTimestamp("updatedAt")
                )
            }
        }
    }

    suspend fun getTempSaveCount(): Int {
        val user = auth.currentUser ?: throw IllegalStateException("로그인이 필요합니다.")
        val snapshot = db.collection("TempSaves")
            .whereEqualTo("userId", user.uid)
            .get()
            .await()
        return snapshot.size()
    }

    suspend fun deleteTempSave(documentId: String) {
        db.collection("TempSaves").document(documentId).delete().await()
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
        if (uriString.startsWith("https://firebasestorage.googleapis.com")) {
            return uriString // 이미 업로드된 URL
        }
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

    private fun mapTempSaveDocument(doc: DocumentSnapshot): RecipeData? {
        val title = doc.getString("title") ?: return null
        val description = doc.getString("simpleDescription").orElseEmpty()
        val thumbnailUrl = doc.getString("thumbnailUrl").orElseNull()
        val servings = doc.getLong("servings")?.toInt() ?: 1
        val difficulty = doc.getString("difficulty") ?: "보통"
        val cookingTime = doc.getLong("cookingTime")?.toInt() ?: 0
        val category = doc.get("category").toStringList()
        val tools = doc.get("tools").toStringList()

        val ingredients = (doc.get("ingredients") as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let { map ->
                Ingredient(
                    name = (map["name"] as? String).orElseNull(),
                    amount = (map["amount"] as? String).orElseNull(),
                    unit = (map["unit"] as? String).orElseNull()
                )
            }
        } ?: emptyList()

        val steps = (doc.get("steps") as? List<*>)?.mapNotNull { entry ->
            (entry as? Map<*, *>)?.let { map ->
                RecipeStep_write(
                    imageUri = (map["imageUrl"] as? String).orElseNull(),
                    stepTitle = (map["title"] as? String).orEmpty(),
                    stepDescription = (map["description"] as? String).orEmpty(),
                    timerSeconds = (map["time"] as? Number)?.toInt() ?: 0,
                    useTimer = map["useTimer"] as? Boolean ?: false
                )
            }
        } ?: emptyList()

        return RecipeData(
            thumbnailUrl = thumbnailUrl,
            category = category,
            title = title,
            description = description,
            difficulty = difficulty,
            servings = servings,
            cookingTimeMinutes = cookingTime,
            ingredients = ingredients,
            tools = tools,
            steps = if (steps.isNotEmpty()) steps else listOf(RecipeStep_write())
        )
    }

    private fun Any?.toStringList(): List<String> =
        (this as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    private fun String?.orElseNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.orElseEmpty(): String = this ?: ""
}
