package com.example.recipe_pocket

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.RecipeLoader.db
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await // Firebase Task를 코루틴과 함께 사용하기 위해 필요
import java.text.Normalizer
import kotlin.random.Random

object RecipeLoader {

    private val db: FirebaseFirestore = Firebase.firestore
    /*
     * 지정된 개수만큼 중복되지 않는 랜덤 레시피를 가져오고, 각 레시피의 작성자 정보도 함께 로드합니다.
     * 이 함수는 코루틴 내에서 호출되어야 합니다. (suspend 함수)
     *
     * @param count 가져올 레시피의 수.
     * @return 성공 시 Recipe 객체 리스트를 담은 Result.success, 실패 시 Exception을 담은 Result.failure.
     */
    suspend fun loadMultipleRandomRecipesWithAuthor(
        count: Int
    ): Result<List<Recipe>> {
        return try {
            // 1. 모든 레시피 문서를 가져옵니다.
            val recipeQueryResult = db.collection("Recipes").get().await() // .await()를 사용해 코루틴으로 변환

            if (recipeQueryResult == null || recipeQueryResult.isEmpty) {
                return Result.failure(Exception("레시피를 불러오지 못했습니다. (결과 없음)"))
            }

            val allRecipeDocuments = recipeQueryResult.documents
            if (allRecipeDocuments.isEmpty()) {
                return Result.failure(Exception("표시할 레시피가 없습니다. (문서 없음)"))
            }

            // 2. 가져올 레시피 수보다 실제 문서 수가 적으면, 있는 만큼만 가져옵니다.
            val recipesToFetchCount = minOf(count, allRecipeDocuments.size)
            if (recipesToFetchCount == 0) {
                return Result.failure(Exception("선택할 레시피가 없습니다."))
            }

            // 3. 문서를 섞은 후, 필요한 개수만큼 선택하여 중복을 방지합니다.
            val randomSelectedDocuments = allRecipeDocuments.shuffled().take(recipesToFetchCount)

            // 4. 각 선택된 레시피에 대해 작성자 정보를 병렬로 가져옵니다.
            val recipesWithAuthors = coroutineScope { // 자식 코루틴들이 모두 완료될 때까지 기다립니다.
                randomSelectedDocuments.map { recipeDocument ->
                    async { // 각 레시피 정보와 작성자 정보 로드를 비동기(병렬)로 처리합니다.
                        val recipe = recipeDocument.toObject(Recipe::class.java)
                        if (recipe != null) {
                            recipe.userId?.let { userId ->
                                if (userId.isNotEmpty()) {
                                    try {
                                        val userDocument = db.collection("Users").document(userId).get().await()
                                        if (userDocument != null && userDocument.exists()) {
                                            recipe.author = userDocument.toObject(User::class.java)
                                        }
                                    } catch (e: Exception) {
                                        // 사용자 정보 로드 실패 시 오류를 로깅할 수 있지만,
                                        // 레시피 자체는 반환하도록 recipe.author는 null로 둡니다.
                                        System.err.println("작성자 정보 로드 실패 (userId: $userId): ${e.message}")
                                    }
                                }
                            }
                            recipe // author 정보가 채워졌거나, userId가 없거나, 로드 실패한 recipe 반환
                        } else {
                            null // 레시피 객체 변환 실패 시 null 반환
                        }
                    }
                }.awaitAll() // 모든 async 작업이 완료되기를 기다립니다.
                    .filterNotNull() // null (객체 변환 실패한 경우)을 제외한 리스트를 만듭니다.
            }

            if (recipesWithAuthors.isEmpty() && recipesToFetchCount > 0) {
                // 문서는 있었지만, 객체 변환이나 다른 이유로 최종 레시피 리스트가 비었을 경우
                return Result.failure(Exception("레시피 객체를 만들거나 작성자 정보를 가져오는데 실패했습니다."))
            }

            Result.success(recipesWithAuthors)

        } catch (e: Exception) {
            // Firestore 통신 실패 등 전체 과정에서 발생한 예외 처리
            Result.failure(e)
        }
    }

    /**
     * 제목으로 레시피 검색
     */
    suspend fun searchRecipesByTitle(query: String): Result<List<Recipe>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        // 1. 검색어를 소문자로 변환하고 NFC로 정규화
        val normalizedQuery = Normalizer.normalize(query.lowercase().trim(), Normalizer.Form.NFC)

        return try {
            val recipeQueryResult = db.collection("Recipes").get().await()

            if (recipeQueryResult == null || recipeQueryResult.isEmpty) {
                return Result.failure(Exception("레시피를 불러오지 못했습니다. (결과 없음)"))
            }

            val allRecipeObjects = recipeQueryResult.documents.mapNotNull { doc ->
                doc.toObject(Recipe::class.java)?.also { it.id = doc.id }
            }

            val filteredRecipes = allRecipeObjects.filter { recipe ->
                recipe.title?.let { title ->
                    // 2. 레시피 제목도 소문자로 변환하고 NFC로 정규화
                    val normalizedTitle = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFC)
                    normalizedTitle.contains(normalizedQuery)
                } ?: false // 제목이 null이면 false 반환
            }

            if (filteredRecipes.isEmpty()) {
                return Result.success(emptyList())
            }

            val recipesWithAuthors = coroutineScope {
                filteredRecipes.map { recipe ->
                    async {
                        recipe.userId?.let { userId ->
                            if (userId.isNotEmpty()) {
                                try {
                                    val userDocument = db.collection("Users").document(userId).get().await()
                                    if (userDocument != null && userDocument.exists()) {
                                        recipe.author = userDocument.toObject(User::class.java)
                                    }
                                } catch (e: Exception) {
                                    System.err.println("작성자 정보 로드 실패 (search) (userId: $userId): ${e.message}")
                                }
                            }
                        }
                        recipe
                    }
                }.awaitAll()
            }
            Result.success(recipesWithAuthors)

        } catch (e: Exception) {
            Result.failure(Exception("검색 중 오류 발생: ${e.message}", e))
        }
    }
}
