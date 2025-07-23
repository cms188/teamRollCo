package com.example.recipe_pocket.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Recipe(
    @DocumentId
    var id: String? = null,
    val userId: String? = null,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val cookingTime: Int? = null,
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
    val bookmarkedBy: List<String>? = null,
    val tags: List<String>? = null,

    @get:Exclude @set:Exclude
    var author: User? = null,
    @get:Exclude @set:Exclude
    var isBookmarked: Boolean = false
) {
    constructor() : this(
        id = null, userId = null, title = null, thumbnailUrl = null, cookingTime = null,
        category = null, createdAt = null, difficulty = null, simpleDescription = null,
        steps = null, tools = null, updatedAt = null, userEmail = null,
        servings = null, ingredients = null, bookmarkedBy = null, // 추가
        author = null, isBookmarked = false // 추가
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

@IgnoreExtraProperties
data class User(
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val title: String? = null
) {
    constructor() : this(null, null)
}