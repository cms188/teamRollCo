package com.example.recipe_pocket.repository

import android.util.Log
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.User
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    // 인기 레시피를 불러오는 함수 (좋아요 순)
    suspend fun loadPopularRecipes(count: Int): Result<List<Recipe>> {
        return try {
            val querySnapshot = db.collection("Recipes")
                .orderBy("likeCount", Query.Direction.DESCENDING)
                .limit(count.toLong())
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


    // 15분 이하 레시피를 불러오는 함수
    suspend fun loadRecipesByCookingTime(maxTime: Int, count: Int): Result<List<Recipe>> {
        return try {
            val querySnapshot = db.collection("Recipes")
                .whereLessThanOrEqualTo("cookingTime", maxTime)
                .limit(count.toLong())
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

    // 제목 + 재료 검색
    suspend fun searchRecipes(query: String): Result<List<Recipe>> {
        if (query.isBlank()) return Result.success(emptyList())

        val normalizedQuery = Normalizer.normalize(query.lowercase().trim(), Normalizer.Form.NFC)
        return try {
            // Firestore에서는 OR 쿼리가 복잡하므로, 일단 모든 레시피를 가져와서 클라이언트에서 필터링
            val recipeQueryResult = db.collection("Recipes").get().await()

            val filteredDocuments = recipeQueryResult.documents.filter { doc ->
                val title = doc.getString("title") ?: ""
                val titleMatches = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFC).contains(normalizedQuery)

                val ingredients = doc.get("ingredients") as? List<Map<String, Any>>
                val ingredientMatches = ingredients?.any { ingredient ->
                    val name = ingredient["name"] as? String ?: ""
                    Normalizer.normalize(name.lowercase(), Normalizer.Form.NFC).contains(normalizedQuery)
                } ?: false

                titleMatches || ingredientMatches // 제목 또는 재료명에 포함되면 true
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


    suspend fun loadRecipesByCategory(category: String): Result<List<Recipe>> {
        return try {
            val querySnapshot = db.collection("Recipes")
                .whereArrayContains("category", category)
                .get()
                .await()

            // category 필드가 String인 경우도 처리
            val stringCategorySnapshot = db.collection("Recipes")
                .whereEqualTo("category", category)
                .get()
                .await()

            // 두 쿼리 결과를 합침 (중복 제거)
            val allDocuments = (querySnapshot.documents + stringCategorySnapshot.documents)
                .distinctBy { it.id }

            val recipes = coroutineScope {
                allDocuments.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }

            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(Exception("카테고리별 레시피 로드 중 오류 발생: ${e.message}", e))
        }
    }

    // 모든 레시피를 가져오는 함수
    suspend fun loadAllRecipes(): Result<List<Recipe>> {
        return try {
            val querySnapshot = db.collection("Recipes")
                .get()
                .await()

            val recipes = coroutineScope {
                querySnapshot.documents.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }

            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(Exception("전체 레시피 로드 중 오류 발생: ${e.message}", e))
        }
    }

    // 사용자 ID로 레시피를 가져오는 함수
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
            Result.failure(Exception("사용자 레시피 로드 중 오류 발생: ${e.message}", e))
        }
    }
}