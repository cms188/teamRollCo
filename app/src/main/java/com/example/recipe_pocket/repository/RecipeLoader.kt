package com.example.recipe_pocket.repository

import android.util.Log
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.User
import com.google.firebase.Timestamp
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
import java.util.Calendar

object RecipeLoader {

    private val db: FirebaseFirestore = Firebase.firestore
    private val auth = Firebase.auth

    // 사용자의 알레르기 키워드 목록을 가져오는 함수
    private suspend fun getUserAllergyKeywords(): List<String>? {
        val userId = Firebase.auth.currentUser?.uid ?: return null
        return try {
            val document = db.collection("Users").document(userId).get().await()
            // Firestore에서 'allergyKeywords' 필드를 List<String>으로 가져옴
            document.get("allergyKeywords") as? List<String>
        } catch (e: Exception) {
            Log.e("RecipeLoader", "알레르기 키워드 로딩 실패", e)
            null
        }
    }

    // 알레르기 키워드를 기반으로 레시피 목록을 필터링하는 함수
    private suspend fun filterRecipesByAllergies(recipes: List<Recipe>): List<Recipe> {
        // 사용자 알레르기 키워드 가져오기
        val allergyKeywords = getUserAllergyKeywords()
        // 키워드가 없거나 비어있으면 원본 목록 반환
        if (allergyKeywords.isNullOrEmpty()) {
            return recipes
        }

        // 레시피 목록에서 알레르기 재료가 포함된 레시피를 제외
        return recipes.filterNot { recipe ->
            // 레시피의 재료 목록(ingredients)을 확인
            recipe.ingredients?.any { ingredient ->
                val ingredientName = ingredient.name ?: ""
                // 등록된 알레르기 키워드 중 하나라도 재료 이름에 포함되는지 확인 (대소문자 무시)
                allergyKeywords.any { keyword ->
                    ingredientName.contains(keyword, ignoreCase = true)
                }
            } == true
        }
    }


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
            val filteredRecipes = filterRecipesByAllergies(recipes)
            Result.success(filteredRecipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // [수정] 24시간 내 조회수 기반 인기 레시피 로드 + 5개 보장 로직
    suspend fun loadPopularRecipesByRecentViews(count: Int): Result<List<Recipe>> {
        return try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, -24)
            val twentyFourHoursAgo = Timestamp(calendar.time)

            val statsSnapshot = db.collection("recipeViewStats")
                .whereGreaterThan("viewedAt", twentyFourHoursAgo)
                .get()
                .await()

            if (statsSnapshot.isEmpty) {
                return loadMultipleRandomRecipesWithAuthor(count)
            }

            val viewCounts = statsSnapshot.documents
                .mapNotNull { it.getString("recipeId") }
                .groupingBy { it }
                .eachCount()

            val topRecipeIds = viewCounts.entries
                .sortedByDescending { it.value }
                .take(count)
                .map { it.key }

            if (topRecipeIds.isEmpty()) {
                return loadMultipleRandomRecipesWithAuthor(count)
            }

            val recipesSnapshot = db.collection("Recipes")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), topRecipeIds)
                .get()
                .await()

            val popularRecipes = coroutineScope {
                recipesSnapshot.documents.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }

            val sortedPopularRecipes = popularRecipes.sortedByDescending { recipe ->
                viewCounts[recipe.id] ?: 0
            }

            var finalRecipes = sortedPopularRecipes
            val remainingCount = count - finalRecipes.size
            if (remainingCount > 0) {
                val existingIds = finalRecipes.map { it.id }.toSet()
                val randomFillerResult = loadMultipleRandomRecipesWithExclusion(remainingCount, existingIds)
                randomFillerResult.onSuccess { fillerRecipes ->
                    finalRecipes = finalRecipes + fillerRecipes
                }
            }

            val filteredRecipes = filterRecipesByAllergies(finalRecipes)
            Result.success(filteredRecipes)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // [추가] 특정 ID를 제외하고 랜덤 레시피를 가져오는 함수
    private suspend fun loadMultipleRandomRecipesWithExclusion(count: Int, excludedIds: Set<String?>): Result<List<Recipe>> {
        return try {
            val recipeQueryResult = db.collection("Recipes").get().await()
            if (recipeQueryResult.isEmpty) return Result.success(emptyList())

            val availableDocuments = recipeQueryResult.documents.filterNot { excludedIds.contains(it.id) }
            val recipesToFetchCount = minOf(count, availableDocuments.size)
            val randomDocuments = availableDocuments.shuffled().take(recipesToFetchCount)

            val recipes = coroutineScope {
                randomDocuments.map { doc ->
                    async { enrichRecipeWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            // 이 함수 내부에서는 알레르기 필터링을 하지 않음 (상위 함수에서 최종적으로 처리)
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
            val filteredRecipes = filterRecipesByAllergies(recipes)
            Result.success(filteredRecipes)
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
            val filteredRecipes = filterRecipesByAllergies(recipes)
            Result.success(filteredRecipes)
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
            val filteredRecipes = filterRecipesByAllergies(recipes)
            Result.success(filteredRecipes)
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

            val filteredRecipes = filterRecipesByAllergies(recipes)
            Result.success(filteredRecipes)
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

            val filteredRecipes = filterRecipesByAllergies(recipes)
            Result.success(filteredRecipes)
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