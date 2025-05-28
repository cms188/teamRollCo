package com.example.recipe_pocket

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties // Firestore 문서에 있는 모든 필드를 매핑할 필요 없을 때 사용
data class Recipe(
    val userId: String? = null,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val cookingTime: Int? = null,

    @get:Exclude @set:Exclude // 이 어노테이션이 중요합니다.
    var author: User? = null     // 'var'로 선언되고 'User?' 타입이어야 합니다.
) {
    // Firestore가 빈 생성자를 요구하므로 추가합니다.
    constructor() : this(null, null, null)
}

@IgnoreExtraProperties
data class User(
    val nickname: String? = null, // Firestore "Users" 문서의 닉네임 필드명 (예: "nickname")
    val profileImageUrl: String? = null // Firestore "Users" 문서의 프로필 이미지 URL 필드명 (예: "profileImageUrl")
    // 필요한 다른 사용자 정보 필드 추가 가능
) {
    // Firestore가 빈 생성자를 요구하므로 추가합니다.
    constructor() : this(null, null)
}