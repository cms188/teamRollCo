package com.example.recipe_pocket.repository

import android.util.Log
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.User
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.text.Normalizer

object RecipeLoader {

    private val db: FirebaseFirestore = Firebase.firestore
    private val auth = Firebase.auth

    suspend fun enrichRecipeWithAuthor(document: DocumentSnapshot): Recipe? {
        val recipe = document.toObject(Recipe::class.java) ?: return null
        recipe.id = document.id

        auth.currentUser?.uid?.let { currentUserId ->
            recipe.isBookmarked = recipe.bookmarkedBy?.contains(currentUserId) == true
            recipe.isLiked = recipe.likedBy?.contains(currentUserId) == true
        }

        try {
            val reviewsSnapshot = db.collection("Recipes").document(recipe.id!!).collection("Reviews").get().await()
            val reviews = reviewsSnapshot.toObjects(com.example.recipe_pocket.data.Review::class.java)

            recipe.reviewCount = reviews.size
            if (reviews.isNotEmpty()) {
                recipe.averageRating = reviews.map { it.rating }.average().toFloat()
            }
        } catch (e: Exception) {
            System.err.println("리뷰 정보 로드 실패 (recipeId: ${recipe.id}): ${e.message}")
            recipe.reviewCount = 0
            recipe.averageRating = 0.0f
        }

        recipe.userId?.let { authorId ->
            if (authorId.isNotEmpty()) {
                try {
                    val userDoc = db.collection("Users").document(authorId).get().await()
                    if (userDoc.exists()) {
                        recipe.author = userDoc.toObject(User::class.java)
                    }
                } catch (e: Exception) {
                    System.err.println("작성자 정보 로드 실패 (userId: $authorId): ${e.message}")
                }
            }
        }
        return recipe
    }

    suspend fun loadUsers(userIds: List<String>): Result<Map<String, User>> {
        if (userIds.isEmpty()) {
            return Result.success(emptyMap())
        }
        return try {
            val usersMap = mutableMapOf<String, User>()
            userIds.chunked(30).forEach { chunk ->
                val snapshot = db.collection("Users").whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk).get().await()
                for (doc in snapshot.documents) {
                    doc.toObject(User::class.java)?.let { user ->
                        usersMap[doc.id] = user
                    }
                }
            }
            Result.success(usersMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun loadSingleRecipeWithAuthor(recipeId: String): Result<Recipe?> {
        return try {
            val documentSnapshot = db.collection("Recipes").document(recipeId).get().await()

            if (documentSnapshot.exists()) {
                val recipe = enrichRecipeWithAuthor(documentSnapshot)
                Result.success(recipe)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun loadMultipleRandomRecipesWithAuthor(count: Int): Result<List<Recipe>> {
        return try {
            val recipeQueryResult = db.collection("Recipes").get().await()
            if (recipeQueryResult.isEmpty) return Result.success(emptyList())

            val recipesToFetchCount = minOf(count, recipeQueryResult.size())
            val randomDocuments = recipeQueryResult.documents.shuffled().take(recipesToFetchCount)

            val recipes = coroutineScope {
                randomDocuments.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchRecipesByTitle(query: String): Result<List<Recipe>> {
        if (query.isBlank()) return Result.success(emptyList())

        val normalizedQuery = Normalizer.normalize(query.lowercase().trim(), Normalizer.Form.NFC)
        return try {
            val recipeQueryResult = db.collection("Recipes").get().await()

            val filteredDocuments = recipeQueryResult.documents.filter { doc ->
                val title = doc.getString("title") ?: ""
                Normalizer.normalize(title.lowercase(), Normalizer.Form.NFC).contains(normalizedQuery)
            }

            val recipes = coroutineScope {
                filteredDocuments.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(Exception("검색 중 오류 발생: ${e.message}", e))
        }
    }

    suspend fun loadBookmarkedRecipes(): Result<List<Recipe>> {
        val currentUserId = auth.currentUser?.uid ?: return Result.success(emptyList())

        return try {
            val querySnapshot = db.collection("Recipes")
                .whereArrayContains("bookmarkedBy", currentUserId)
                .get()
                .await()

            val recipes = coroutineScope {
                querySnapshot.documents.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 사용자가 좋아요한 레시피 목록을 불러오는 함수
    suspend fun loadLikedRecipes(): Result<List<Recipe>> {
        val currentUserId = auth.currentUser?.uid ?: return Result.success(emptyList())

        return try {
            val querySnapshot = db.collection("Recipes")
                .whereArrayContains("likedBy", currentUserId)
                .get()
                .await()

            val recipes = coroutineScope {
                querySnapshot.documents.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun loadRecipesByUserId(userId: String): Result<List<Recipe>> {
        return try {
            val querySnapshot = db.collection("Recipes")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val recipes = coroutineScope {
                querySnapshot.documents.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadAllRecipes(): Result<List<Recipe>> {
        return try {
            val recipeQueryResult = db.collection("Recipes").get().await()
            if (recipeQueryResult.isEmpty) {
                return Result.success(emptyList())
            }

            val recipes = coroutineScope {
                recipeQueryResult.documents.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }

            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}