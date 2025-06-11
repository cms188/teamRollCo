package com.example.recipe_pocket

import java.io.Serializable
import java.util.UUID

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
    var steps: List<RecipeStep_write> = emptyList()
) : Serializable

// ⭐ 변경점: Ingredient 클래스를 Firestore와 호환되도록 수정하고, 이 파일을 유일한 정의 파일로 사용합니다.
// non-nullable 필드를 nullable로 변경하고, 기본값을 null로 설정하며, 빈 생성자를 추가합니다.
data class Ingredient(
    val name: String? = null,
    val amount: String? = null,
    val unit: String? = null
) : Serializable {
    // Firestore가 toObject() 변환 시 사용할 빈 생성자
    constructor() : this(null, null, null)
}

// 조리 단계 정보
data class RecipeStep_write(
    val id: String = UUID.randomUUID().toString(), // 모든 인스턴스가 고유한 ID를 갖도록 함
    var imageUri: String? = null,
    var stepTitle: String = "",
    var stepDescription: String = "",
    var timerMinutes: Int = 0,
    var useTimer: Boolean = false
) : Serializable