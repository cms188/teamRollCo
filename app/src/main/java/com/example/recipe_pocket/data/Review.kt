package com.example.recipe_pocket.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Review(
    @DocumentId
    var id: String? = null,
    val recipeId: String? = null,
    val recipeTitle: String? = null,
    val userId: String? = null,
    val userNickname: String? = null,
    val userProfileUrl: String? = null,
    val rating: Float = 0f,
    val flavor: String? = null,
    val difficulty: String? = null,
    val comment: String? = null,
    val imageUrls: List<String>? = null,
    val createdAt: Timestamp? = null
) {
    constructor() : this(null, null, null, null, null, null, 0f, null, null, null, null, null)
}