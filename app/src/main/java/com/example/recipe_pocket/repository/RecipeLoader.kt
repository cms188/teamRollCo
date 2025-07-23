package com.example.recipe_pocket.repository

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

    private suspend fun enrichRecipeWithAuthor(document: DocumentSnapshot): Recipe? {
        val recipe = document.toObject(Recipe::class.java) ?: return null
        recipe.id = document.id

        // 현재 로그인한 사용자의 북마크 상태 설정
        auth.currentUser?.uid?.let { currentUserId ->
            recipe.isBookmarked = recipe.bookmarkedBy?.contains(currentUserId) == true
        }

        // 작성자 정보 로드
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

    suspend fun loadSingleRecipeWithAuthor(recipeId: String): Result<Recipe?> {
        return try {
            // Firestore에서 해당 ID의 레시피 문서를 가져옴
            val documentSnapshot = db.collection("Recipes").document(recipeId).get().await()

            if (documentSnapshot.exists()) {
                // 문서가 존재하면, 작성자 정보를 포함하여 Recipe 객체로 변환
                val recipe = enrichRecipeWithAuthor(documentSnapshot)
                Result.success(recipe)
            } else {
                // 문서가 존재하지 않으면 null을 반환
                Result.success(null)
            }
        } catch (e: Exception) {
            // 작업 중 예외 발생 시 Result.failure 반환
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
}