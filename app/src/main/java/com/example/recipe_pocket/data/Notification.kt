package com.example.recipe_pocket.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * 알림 유형을 정의하는 Enum 클래스
 * - LIKE: 좋아요
 * - REVIEW: 리뷰
 * - FOLLOW: 팔로우
 * - TITLE: 칭호 획득
 */
enum class NotificationType {
    LIKE, REVIEW, FOLLOW, TITLE
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

    val relatedContentId: String? = null, // 레시피 ID (LIKE, REVIEW), 팔로우한 사용자 ID 등
    val recipeTitle: String? = null, // 레시피 제목 (LIKE, REVIEW)
    val recipeThumbnailUrl: String? = null, // 레시피 썸네일 (LIKE, REVIEW)
    val titleName: String? = null, // 획득한 칭호 이름 (TITLE)

    var createdAt: Timestamp? = null, // 알림 생성 또는 마지막 업데이트 시간

    var isRead: Boolean = false // 사용자가 알림을 읽었는지 여부
) {

    //Firestore가 데이터를 객체로 변환할 때 필요한 빈 생성자
    constructor() : this(
        id = null,
        userId = null,
        type = null,
        senderId = null,
        senderName = null,
        senderProfileUrl = null,
        aggregatedUserIds = null,
        relatedContentId = null,
        recipeTitle = null,
        recipeThumbnailUrl = null,
        titleName = null,
        createdAt = Timestamp.now(), // 기본값으로 현재 시간 설정
        isRead = false // 기본값으로 '읽지 않음' 상태
    )
}