package com.example.recipe_pocket.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class TipComment(
    @DocumentId
    var id: String? = null,
    val tipId: String? = null,
    val userId: String? = null,
    val userNickname: String? = null,
    val userProfileUrl: String? = null,
    val comment: String? = null,
    val createdAt: Timestamp? = null,

    // 답글 기능
    val parentId: String? = null, // 부모 댓글의 ID (일반 댓글은 null)
    val recipientId: String? = null, // 답글을 받는 사용자의 ID
    val recipientNickname: String? = null // 답글을 받는 사용자의 닉네임
) {
    constructor() : this(null, null, null, null, null, null, null, null, null, null)
}