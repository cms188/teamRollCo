package com.example.recipe_pocket.data

import com.google.firebase.Timestamp
import java.io.Serializable
import java.util.UUID

data class RecipeData(
    // 01. 기본 정보
    var thumbnailUrl: String? = null, // Uri -> String
    var category: List<String> = emptyList(),
    var title: String = "",
    var description: String = "",
    var difficulty: String = "보통",
    var servings: Int = 1,
    var cookingTimeMinutes: Int = 0,

    // 02. 재료 및 도구
    var ingredients: List<Ingredient> = emptyList(),
    var tools: List<String> = emptyList(),

    // 03. 조리 단계
    var steps: List<RecipeStep_write> = emptyList()
) : Serializable

// Ingredient 클래스
data class Ingredient(
    var name: String? = null,
    var amount: String? = null,
    var unit: String? = null
) : Serializable {
    constructor() : this(null, null, null)
}

// 조리 단계 정보
data class RecipeStep_write(
    val id: String = UUID.randomUUID().toString(),
    var imageUri: String? = null,
    var stepTitle: String = "",
    var stepDescription: String = "",
    var timerSeconds: Int = 0,
    var useTimer: Boolean = false
) : Serializable

data class TempSaveDraft(
    val id: String,
    val recipe: RecipeData,
    val updatedAt: Timestamp?
)
