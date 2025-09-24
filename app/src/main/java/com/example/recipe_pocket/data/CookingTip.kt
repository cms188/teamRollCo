package com.example.recipe_pocket.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import java.io.Serializable

data class CookingTip(
    @DocumentId
    var id: String? = null,
    val userId: String? = null,
    val title: String? = null,
    val content: List<ContentBlock>? = null,
    val createdAt: Timestamp? = null,

    val likedBy: List<String>? = null, // 추천한 사용자 ID 목록
    val dislikedBy: List<String>? = null, // 비추천한 사용자 ID 목록

    // 집계를 위한 필드 (규칙 기반 추천/비추천 필터링에 사용)
    val recommendationScore: Int = 0,

    @get:Exclude @set:Exclude
    var author: User? = null, // 작성자 정보
    @get:Exclude @set:Exclude
    var commentCount: Int = 0, // 댓글 수
    @get:Exclude @set:Exclude
    var isLiked: Boolean = false, // 현재 사용자가 추천했는지 여부
    @get:Exclude @set:Exclude
    var isDisliked: Boolean = false // 현재 사용자가 비추천했는지 여부
) : Serializable { // Serializable 구현
    constructor() : this(id = null)
}

data class ContentBlock(
    val text: String? = null,
    val imageUrl: String? = null
) : Serializable { // Serializable 구현
    constructor() : this(null, null)
}