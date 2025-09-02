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

    private const val TAG = "NotificationHandler_FINAL"
    private val db = Firebase.firestore

    /**
     * 좋아요/리뷰 알림을 추가하거나 기존 그룹에 참여시키는 함수.
     */
    suspend fun addLikeReviewNotification(
        recipientId: String, senderId: String, recipe: Recipe, type: NotificationType
    ) {
        if (recipientId == senderId) return
        Log.d(TAG, "[$type ADD] 처리 시작: 받는사람($recipientId), 보낸사람($senderId), 레시피(${recipe.id})")

        try {
            // 1시간 이내에 만들어진, '읽지 않은' 그룹화 가능한 알림을 찾음
            val oneHourAgo = Calendar.getInstance().apply { add(Calendar.HOUR, -1) }.time
            val groupQuery = db.collection("Notifications")
                .whereEqualTo("userId", recipientId)
                .whereEqualTo("type", type.name)
                .whereEqualTo("relatedContentId", recipe.id)
                .whereEqualTo("isRead", false)
                .whereGreaterThan("createdAt", Timestamp(oneHourAgo))
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get().await()

            val senderUser = db.collection("Users").document(senderId).get().await().toObject(User::class.java) ?: User("알 수 없음", null)

            if (groupQuery.isEmpty) {
                // 참여할 그룹이 없으면, 이 사용자가 보낸 알림이 이미 있는지 확인
                val ownNotifQuery = db.collection("Notifications")
                    .whereEqualTo("userId", recipientId)
                    .whereEqualTo("type", type.name)
                    .whereEqualTo("relatedContentId", recipe.id)
                    .whereArrayContains("aggregatedUserIds", senderId)
                    .limit(1)
                    .get().await()

                if (ownNotifQuery.isEmpty) {
                    // 이 사용자가 보낸 알림도 없으면, 새로 생성
                    val newNotification = Notification(
                        userId = recipientId, type = type, senderId = senderId, senderName = senderUser.nickname,
                        senderProfileUrl = senderUser.profileImageUrl, aggregatedUserIds = listOf(senderId),
                        relatedContentId = recipe.id, recipeTitle = recipe.title, recipeThumbnailUrl = recipe.thumbnailUrl,
                        createdAt = Timestamp.now(),
                        isRead = false
                    )
                    val newDoc = db.collection("Notifications").add(newNotification).await()
                    Log.d(TAG, "[$type ADD] 참여할 그룹/기존 알림 없음. 새 알림(${newDoc.id}) 생성.")
                } else {
                    // 이미 이 사용자가 보낸 알림이 있다면, isRead를 false로 바꾸고 시간만 갱신 (재활성화)
                    val docToUpdate = ownNotifQuery.documents.first()
                    docToUpdate.reference.update(mapOf("isRead" to false, "createdAt" to Timestamp.now())).await()
                    Log.d(TAG, "[$type ADD] 기존 알림(${docToUpdate.id}) 재활성화 및 시간 갱신.")
                }
            } else {
                // 참여할 그룹이 있으면, 해당 그룹에 참여하고 정보 업데이트
                val groupDoc = groupQuery.documents.first()
                groupDoc.reference.update(
                    mapOf(
                        "senderId" to senderId, "senderName" to senderUser.nickname,
                        "senderProfileUrl" to senderUser.profileImageUrl,
                        "aggregatedUserIds" to FieldValue.arrayUnion(senderId), "createdAt" to Timestamp.now()
                    )
                ).await()
                Log.d(TAG, "[$type ADD] 기존 그룹(${groupDoc.id})에 참여.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$type ADD] 알림 추가/그룹화 실패!", e)
        }
    }

    /**
     * 좋아요/리뷰 알림을 제거하는 함수.
     */
    suspend fun removeLikeReviewNotification(
        recipientId: String, senderId: String, recipeId: String, type: NotificationType
    ) {
        if (recipientId == senderId) return
        Log.d(TAG, "[$type REMOVE] 처리 시작: 받는사람($recipientId), 보낸사람($senderId), 레시피($recipeId)")
        try {
            // 이 sender가 포함된, 읽지 않은 모든 관련 알림을 찾음
            val query = db.collection("Notifications")
                .whereEqualTo("userId", recipientId)
                .whereEqualTo("type", type.name)
                .whereEqualTo("relatedContentId", recipeId)
                .whereEqualTo("isRead", false)
                .whereArrayContains("aggregatedUserIds", senderId)
                .get().await()

            if (query.isEmpty) {
                Log.d(TAG, "[$type REMOVE] 제거할 알림 없음.")
                return
            }
            val batch = db.batch()
            for (doc in query.documents) {
                val userIds = doc.get("aggregatedUserIds") as? List<*> ?: emptyList<String>()
                if (userIds.size > 1) {
                    batch.update(doc.reference, "aggregatedUserIds", FieldValue.arrayRemove(senderId))
                } else {
                    batch.delete(doc.reference)
                }
            }
            batch.commit().await()
            Log.d(TAG, "[$type REMOVE] ${query.size()}개 알림에서 사용자 제거/삭제 완료.")
        } catch (e: Exception) {
            Log.e(TAG, "[$type REMOVE] 알림 제거 실패!", e)
        }
    }

    /**
     * 팔로우 알림을 추가하거나 기존 그룹에 참여시키는 함수.
     */
    suspend fun addFollowNotification(recipientId: String, senderId: String) {
        if (recipientId == senderId) return
        Log.d(TAG, "[FOLLOW ADD] 처리 시작: 받는사람($recipientId), 보낸사람($senderId)")
        try {
            val oneHourAgo = Calendar.getInstance().apply { add(Calendar.HOUR, -1) }.time
            val groupQuery = db.collection("Notifications")
                .whereEqualTo("userId", recipientId)
                .whereEqualTo("type", NotificationType.FOLLOW.name)
                .whereEqualTo("isRead", false)
                .whereGreaterThan("createdAt", Timestamp(oneHourAgo))
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1).get().await()

            val senderUser = db.collection("Users").document(senderId).get().await().toObject(User::class.java) ?: User("알 수 없음", null)

            if (groupQuery.isEmpty) {
                val ownNotifQuery = db.collection("Notifications")
                    .whereEqualTo("userId", recipientId)
                    .whereEqualTo("type", NotificationType.FOLLOW.name)
                    .whereArrayContains("aggregatedUserIds", senderId)
                    .limit(1).get().await()

                if (ownNotifQuery.isEmpty) {
                    val newNotification = Notification(
                        userId = recipientId, type = NotificationType.FOLLOW, senderId = senderId,
                        senderName = senderUser.nickname, senderProfileUrl = senderUser.profileImageUrl,
                        aggregatedUserIds = listOf(senderId), relatedContentId = senderId, createdAt = Timestamp.now(),
                        isRead = false // ★★★ 명시적으로 isRead 상태 추가
                    )
                    val newDoc = db.collection("Notifications").add(newNotification).await()
                    Log.d(TAG, "[FOLLOW ADD] 새 알림(${newDoc.id}) 생성.")
                } else {
                    val docToUpdate = ownNotifQuery.documents.first()
                    docToUpdate.reference.update(mapOf("isRead" to false, "createdAt" to Timestamp.now())).await()
                    Log.d(TAG, "[FOLLOW ADD] 기존 알림(${docToUpdate.id}) 재활성화.")
                }
            } else {
                val groupDoc = groupQuery.documents.first()
                groupDoc.reference.update(
                    mapOf(
                        "senderId" to senderId, "senderName" to senderUser.nickname,
                        "senderProfileUrl" to senderUser.profileImageUrl, "relatedContentId" to senderId,
                        "aggregatedUserIds" to FieldValue.arrayUnion(senderId), "createdAt" to Timestamp.now()
                    )
                ).await()
                Log.d(TAG, "[FOLLOW ADD] 기존 그룹(${groupDoc.id})에 참여.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[FOLLOW ADD] 알림 추가/그룹화 실패", e)
        }
    }

    /**
     * 팔로우 취소 시 관련 알림을 제거하는 함수.
     */
    suspend fun removeFollowNotification(recipientId: String, senderId: String) {
        if (recipientId == senderId) return
        Log.d(TAG, "[FOLLOW REMOVE] 처리 시작: 받는사람($recipientId), 보낸사람($senderId)")
        try {
            val query = db.collection("Notifications")
                .whereEqualTo("userId", recipientId)
                .whereEqualTo("type", NotificationType.FOLLOW.name)
                .whereEqualTo("isRead", false)
                .whereArrayContains("aggregatedUserIds", senderId)
                .get().await()

            if (query.isEmpty) {
                Log.d(TAG, "[FOLLOW REMOVE] 제거할 알림 없음.")
                return
            }
            val batch = db.batch()
            for (doc in query.documents) {
                val userIds = doc.get("aggregatedUserIds") as? List<*> ?: emptyList<String>()
                if (userIds.size > 1) {
                    batch.update(doc.reference, "aggregatedUserIds", FieldValue.arrayRemove(senderId))
                } else {
                    batch.delete(doc.reference)
                }
            }
            batch.commit().await()
            Log.d(TAG, "[FOLLOW REMOVE] ${query.size()}개 알림에서 사용자 제거/삭제 완료.")
        } catch (e: Exception) {
            Log.e(TAG, "[FOLLOW REMOVE] 알림 제거 실패!", e)
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
                createdAt = Timestamp.now(),
                isRead = false
            )
            val newDoc = db.collection("Notifications").add(newNotification).await()
            Log.d(TAG, "[TITLE] 칭호 알림(${newDoc.id}) 생성 완료: 사용자($userId), 칭호($title)")
        } catch (e: Exception) {
            Log.e(TAG, "Title 알림 생성 실패", e)
        }
    }
}