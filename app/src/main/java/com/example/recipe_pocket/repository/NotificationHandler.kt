package com.example.recipe_pocket.repository

import android.util.Log
import com.example.recipe_pocket.data.Notification
import com.example.recipe_pocket.data.NotificationType
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Calendar

object NotificationHandler {

    private const val TAG = "NotificationHandler"
    private val db = Firebase.firestore

    /**
     * 좋아요 또는 리뷰 알림을 생성하거나 기존 알림을 업데이트(그룹화)
     * @param recipientId 알림을 받을 사용자 ID
     * @param senderId 알림을 발생시킨 사용자 ID
     * @param recipe 관련된 레시피 객체
     * @param type 알림 유형 (LIKE 또는 REVIEW)
     */
    suspend fun createOrUpdateLikeReviewNotification(
        recipientId: String,
        senderId: String,
        recipe: Recipe,
        type: NotificationType
    ) {
        if (recipientId == senderId) return // 자기 자신에게는 알림 보내지 않음

        // 1시간 이내의 읽지 않은, 동일한 레시피에 대한, 동일한 타입의 알림을 찾습니다.
        val oneHourAgo = Calendar.getInstance().apply { add(Calendar.HOUR, -1) }.time
        val recentNotifQuery = db.collection("Notifications")
            .whereEqualTo("userId", recipientId)
            .whereEqualTo("type", type.name)
            .whereEqualTo("relatedContentId", recipe.id)
            .whereEqualTo("isRead", false)
            .whereGreaterThan("createdAt", Timestamp(oneHourAgo))
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)

        try {
            val querySnapshot = recentNotifQuery.get().await()
            val senderUserDoc = db.collection("Users").document(senderId).get().await()
            val sender = senderUserDoc.toObject(User::class.java) ?: User("알 수 없음", null)

            if (querySnapshot.isEmpty) {
                // 그룹화할 알림이 없으면 새로운 알림 생성
                val newNotification = Notification(
                    userId = recipientId,
                    type = type,
                    senderId = senderId,
                    senderName = sender.nickname,
                    senderProfileUrl = sender.profileImageUrl,
                    aggregatedUserIds = listOf(senderId), // 그룹화 시작
                    relatedContentId = recipe.id,
                    recipeTitle = recipe.title,
                    recipeThumbnailUrl = recipe.thumbnailUrl,
                    createdAt = Timestamp.now()
                )
                db.collection("Notifications").add(newNotification).await()
            } else {
                val existingNotifDoc = querySnapshot.documents.first()
                val aggregatedIds = existingNotifDoc.get("aggregatedUserIds") as? List<*> ?: emptyList<String>()
                if (!aggregatedIds.contains(senderId)) { // 중복 상호작용 방지
                    db.collection("Notifications").document(existingNotifDoc.id)
                        .update(
                            mapOf(
                                "senderId" to senderId, // 가장 최근 상호작용한 사람으로 대표자 변경
                                "senderName" to sender.nickname,
                                "senderProfileUrl" to sender.profileImageUrl,
                                "aggregatedUserIds" to FieldValue.arrayUnion(senderId), // 그룹에 추가
                                "createdAt" to Timestamp.now() // 최신 시간으로 업데이트
                            )
                        ).await()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Like/Review 알림 생성 실패", e)
        }
    }

    /**
     * 팔로우 알림을 생성합니다. 만약 동일한 사용자에 대한 기존 팔로우 알림이 있다면 삭제하고 새로 생성합니다.
     * @param recipientId 알림을 받을 사용자 ID
     * @param senderId 팔로우를 한 사용자 ID
     */
    suspend fun createOrUpdateFollowNotification(recipientId: String, senderId: String) {
        if (recipientId == senderId) return

        try {
            // 1. 기존에 'senderId'가 보낸 'FOLLOW' 타입의 알림이 있는지 찾습니다. (시간, 읽음 여부 무관)
            val existingFollowNotifQuery = db.collection("Notifications")
                .whereEqualTo("userId", recipientId)
                .whereEqualTo("type", NotificationType.FOLLOW.name)
                .whereEqualTo("senderId", senderId)
                .get()
                .await()

            // 2. 만약 기존 알림이 있다면, 모두 삭제합니다.
            if (!existingFollowNotifQuery.isEmpty) {
                Log.d(TAG, "기존 팔로우 알림 ${existingFollowNotifQuery.size()}개 발견. 삭제합니다.")
                val batch = db.batch()
                for (doc in existingFollowNotifQuery.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }

            // 3. 사용자 정보를 가져옵니다.
            val senderUserDoc = db.collection("Users").document(senderId).get().await()
            val sender = senderUserDoc.toObject(User::class.java) ?: User("알 수 없음", null)

            // 4. 항상 새로운 알림을 생성합니다.
            val newNotification = Notification(
                userId = recipientId,
                type = NotificationType.FOLLOW,
                senderId = senderId,
                senderName = sender.nickname,
                senderProfileUrl = sender.profileImageUrl,
                aggregatedUserIds = listOf(senderId), // 팔로우는 그룹화하지 않으므로 항상 1명
                relatedContentId = senderId, // 팔로우는 보낸 사람의 프로필로 이동
                createdAt = Timestamp.now()
            )
            db.collection("Notifications").add(newNotification).await()
            Log.d(TAG, "새로운 팔로우 알림 생성 완료. 받는사람: $recipientId, 보낸사람: $senderId")

        } catch (e: Exception) {
            Log.e(TAG, "Follow 알림 생성/업데이트 실패", e)
        }
    }

    /**
     * 칭호 획득 알림을 생성
     * @param userId 칭호를 획득한 사용자 ID
     * @param title 획득한 칭호 이름
     */
    suspend fun createTitleNotification(userId: String, title: String) {
        try {
            val newNotification = Notification(
                userId = userId,
                type = NotificationType.TITLE,
                titleName = title,
                createdAt = Timestamp.now()
            )
            db.collection("Notifications").add(newNotification).await()
        } catch (e: Exception) {
            Log.e(TAG, "Title 알림 생성 실패", e)
        }
    }
}