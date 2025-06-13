package com.example.recipe_pocket

import java.io.Serializable
import java.util.UUID

data class RecipeData(
    // 01. 기본 정보
    var thumbnailUrl: String? = null, // Uri -> String
    var category: String = "기타",
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

// Ingredient 클래스 (변경 없음)
data class Ingredient(
    val name: String? = null,
    val amount: String? = null,
    val unit: String? = null
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