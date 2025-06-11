package com.example.recipe_pocket

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import java.io.Serializable

@IgnoreExtraProperties
data class Recipe(
    @DocumentId
    var id: String? = null,
    val userId: String? = null,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val cookingTime: Int? = null, // 분 단위
    val category: String? = null,
    val createdAt: Timestamp? = null,
    val difficulty: String? = null,
    val simpleDescription: String? = null,
    val steps: List<RecipeStep>? = null,
    val tools: List<String>? = null,
    val updatedAt: Timestamp? = null,
    val userEmail: String? = null,
    val servings: Int? = null,
    val ingredients: List<Ingredient>? = null,

    @get:Exclude @set:Exclude
    var author: User? = null
) {
    constructor() : this(
        id = null, userId = null, title = null, thumbnailUrl = null, cookingTime = null,
        category = null, createdAt = null, difficulty = null, simpleDescription = null,
        steps = null, tools = null, updatedAt = null, userEmail = null,
        servings = null, ingredients = null,
        author = null
    )
}

@IgnoreExtraProperties
data class RecipeStep(
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val stepNumber: Int? = null,
    val time: Int? = null,
    val useTimer: Boolean? = false
) {
    constructor() : this(null, null, null, null, null, false)
}

// ⭐ 변경점: 이 파일에서 Ingredient 클래스 정의를 완전히 삭제했습니다.

@IgnoreExtraProperties
data class User(
    val nickname: String? = null,
    val profileImageUrl: String? = null
) {
    constructor() : this(null, null)
}