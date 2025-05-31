package com.example.recipe_pocket

import com.google.firebase.Timestamp // Firebase Timestamp 임포트
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Recipe(
    // 기존 필드
    @DocumentId // 이 어노테이션이 중요합니다. Firestore가 문서 ID를 이 필드에 자동으로 채워줍니다.
    var id: String? = null,         // Firestore 문서 ID를 저장할 필드 (var로 선언)
    val userId: String? = null,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val cookingTime: Int? = null, // 분 단위

    // 상세 정보 필드 추가
    val category: String? = null,
    val createdAt: Timestamp? = null, // Firestore Timestamp 타입으로 변경
    val difficulty: String? = null,
    val simpleDescription: String? = null,
    val steps: List<RecipeStep>? = null, // RecipeStep 객체 리스트
    val tools: List<String>? = null,
    val updatedAt: Timestamp? = null, // Firestore Timestamp 타입으로 변경
    val userEmail: String? = null, // (userId로 사용자 정보 가져오는 것이 일반적)

    @get:Exclude @set:Exclude
    var author: User? = null
) {
    // Firestore가 빈 생성자를 요구하므로, 모든 필드를 nullable로 하고 기본값을 null로 설정하면
    // 별도의 빈 생성자를 명시적으로 길게 작성하지 않아도 됩니다.
    // 만약 non-null 필드가 있다면 여기에 맞춰서 빈 생성자를 제공해야 합니다.
    constructor() : this(
        id = null, userId = null, title = null, thumbnailUrl = null, cookingTime = null,
        category = null, createdAt = null, difficulty = null, simpleDescription = null,
        steps = null, tools = null, updatedAt = null, userEmail = null, author = null
    )
}

@IgnoreExtraProperties
data class RecipeStep(
    val description: String? = null,
    val imageUrl: String? = null,
    val stepNumber: Int? = null,
    val time: Int? = null, // 해당 스텝 소요 시간 (분)
    val useTimer: Boolean? = false
) {
    constructor() : this(null, null, null, null, false)
}

// User 클래스는 기존 것을 사용합니다.
@IgnoreExtraProperties
data class User(
    val nickname: String? = null,
    val profileImageUrl: String? = null
) {
    constructor() : this(null, null)
}