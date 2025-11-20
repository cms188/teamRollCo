package com.example.recipe_pocket.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

enum class NotificationType {
    LIKE, REVIEW, FOLLOW, TITLE, TIP_LIKE, TIP_COMMENT, TIP_REPLY // 답글 알림 타입 추가
}

data class Notification(
    @DocumentId
    var id: String? = null,

    val userId: String? = null, // 알림을 받는 사용자(수신자)의 ID

    val type: NotificationType? = null, // 알림 종류

    // 알림을 발생시킨 사용자에 대한 정보
    var senderId: String? = null,
    var senderName: String? = null,
    var senderProfileUrl: String? = null,

    // 1시간 이내에 발생한 동일 유형의 알림을 그룹화하기 위한 사용자 ID 목록
    var aggregatedUserIds: List<String>? = null,

    val relatedContentId: String? = null, // 레시피 ID, 팁 ID, 팔로우한 사용자 ID 등
    val recipeTitle: String? = null,
    val recipeThumbnailUrl: String? = null,
    val titleName: String? = null,
    val tipTitle: String? = null, // 요리 팁 제목
    val tipFirstImageUrl: String? = null, // 요리 팁 대표 이미지
    val commentContent: String? = null, // 답글 알림을 위한 댓글 내용 추가

    var createdAt: Timestamp? = null,

    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead: Boolean = false
) {
    constructor() : this(
        id = null, userId = null, type = null, senderId = null, senderName = null,
        senderProfileUrl = null, aggregatedUserIds = null, relatedContentId = null,
        recipeTitle = null, recipeThumbnailUrl = null, titleName = null,
        tipTitle = null, tipFirstImageUrl = null, commentContent = null, // 생성자 초기화
        createdAt = Timestamp.now(), isRead = false
    )
}