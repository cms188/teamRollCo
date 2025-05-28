package com.example.recipe_pocket

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlin.random.Random

object RecipeLoader {

    private val db: FirebaseFirestore = Firebase.firestore

    fun loadRandomRecipeWithAuthor(
        context: Context,
        onSuccess: (recipe: Recipe) -> Unit, // 성공 시 Recipe 객체 (author 정보 포함) 전달
        onFailure: (exception: Exception) -> Unit,
        imageViewToLoad: ImageView? = null,
        placeholderResId: Int? = null,
        errorResId: Int? = null
    ) {
        db.collection("Recipes")
            .get()
            .addOnSuccessListener { recipeResult ->
                if (recipeResult != null && !recipeResult.isEmpty) {
                    val documents = recipeResult.documents
                    if (documents.isNotEmpty()) {
                        val randomIndex = Random.nextInt(documents.size)
                        val randomRecipeDocument = documents[randomIndex]
                        val recipe = randomRecipeDocument.toObject(Recipe::class.java)

                        if (recipe != null) {
                            // 이미지 로드가 필요한 경우
                            imageViewToLoad?.let { imageView ->
                                recipe.thumbnailUrl?.let { url ->
                                    if (url.isNotEmpty()) {
                                        val glideRequest = Glide.with(context).load(url)
                                        placeholderResId?.let { glideRequest.placeholder(it) }
                                        errorResId?.let { glideRequest.error(it) }
                                        glideRequest.into(imageView)
                                    } else {
                                        errorResId?.let { imageView.setImageResource(it) }
                                    }
                                } ?: run {
                                    errorResId?.let { imageView.setImageResource(it) }
                                }
                            }

                            // userId를 사용하여 작성자 정보 가져와서 recipe.author에 할당
                            recipe.userId?.let { userId ->
                                if (userId.isNotEmpty()) {
                                    db.collection("Users").document(userId)
                                        .get()
                                        .addOnSuccessListener { userDocument ->
                                            if (userDocument != null && userDocument.exists()) {
                                                recipe.author = userDocument.toObject(User::class.java)
                                            }
                                            // 사용자 정보가 없거나 가져오기 실패해도 recipe는 전달
                                            onSuccess(recipe)
                                        }
                                        .addOnFailureListener {
                                            // 사용자 정보 가져오기 실패 시에도 recipe는 전달
                                            onSuccess(recipe)
                                        }
                                } else {
                                    // userId가 비어있는 경우
                                    onSuccess(recipe)
                                }
                            } ?: run {
                                // recipe.userId가 null인 경우
                                onSuccess(recipe)
                            }
                        } else {
                            onFailure(Exception("레시피 객체 변환에 실패했습니다."))
                        }
                    } else {
                        onFailure(Exception("표시할 레시피가 없습니다."))
                    }
                } else {
                    onFailure(Exception("레시피를 불러오지 못했습니다."))
                }
            }
            .addOnFailureListener { recipeException ->
                onFailure(recipeException)
            }
    }
}