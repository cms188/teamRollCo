package com.example.recipe_pocket

import java.io.Serializable

// 전체 레시피 데이터를 담는 최상위 클래스
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
    var steps: List<RecipeStep> = emptyList()
) : Serializable

// 재료 정보
data class Ingredient(
    val name: String,
    val amount: String,
    val unit: String
) : Serializable

// 조리 단계 정보
data class RecipeStep(
    var imageUri: String? = null, // Uri -> String
    var stepTitle: String = "",
    var stepDescription: String = "",
    var timerMinutes: Int = 0,
    var useTimer: Boolean = false
) : Serializable