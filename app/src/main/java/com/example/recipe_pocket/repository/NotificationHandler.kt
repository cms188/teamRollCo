package com.example.recipe_pocket.repository

import android.util.Log
import com.example.recipe_pocket.data.CookingTip
import com.example.recipe_pocket.data.Notification
import com.example.recipe_pocket.data.NotificationType
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.User
import com.example.recipe_pocket.data.TipComment
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
     * 레시피 좋아요/리뷰 알림을 추가하거나 기존 그룹에 참여시키는 함수.
     */
    suspend fun addLikeReviewNotification(
        recipientId: String, senderId: String, recipe: Recipe, type: NotificationType
    ) {
        if (recipientId == senderId) return
        Log.d(TAG, "[$type ADD] 처리 시작: 받는사람($recipientId), 보낸사람($senderId), 레시피(${recipe.id})")

        try {
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
                val ownNotifQuery = db.collection("Notifications")
                    .whereEqualTo("userId", recipientId)
                    .whereEqualTo("type", type.name)
                    .whereEqualTo("relatedContentId", recipe.id)
                    .whereArrayContains("aggregatedUserIds", senderId)
                    .limit(1)
                    .get().await()

                if (ownNotifQuery.isEmpty) {
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
                    val docToUpdate = ownNotifQuery.documents.first()
                    docToUpdate.reference.update(mapOf("isRead" to false, "createdAt" to Timestamp.now())).await()
                    Log.d(TAG, "[$type ADD] 기존 알림(${docToUpdate.id}) 재활성화 및 시간 갱신.")
                }
            } else {
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
     * 레시피 좋아요/리뷰 알림을 제거하는 함수.
     */
    suspend fun removeLikeReviewNotification(
        recipientId: String, senderId: String, recipeId: String, type: NotificationType
    ) {
        if (recipientId == senderId) return
        Log.d(TAG, "[$type REMOVE] 처리 시작: 받는사람($recipientId), 보낸사람($senderId), 레시피($recipeId)")
        try {
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
                        isRead = false
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

    /**
     * 요리 팁 댓글 알림을 추가합니다.
     */
    suspend fun addTipCommentNotification(tip: CookingTip, senderId: String) {
        val recipientId = tip.userId ?: return
        if (recipientId == senderId) return // 본인에게는 알림을 보내지 않음

        val firstImageUrl = tip.content?.firstOrNull { !it.imageUrl.isNullOrEmpty() }?.imageUrl

        try {
            val senderUser = db.collection("Users").document(senderId).get().await()
                .toObject(User::class.java) ?: User("알 수 없음", null)

            // 댓글은 그룹화하지 않고 항상 새 알림을 생성합니다.
            val newNotification = Notification(
                userId = recipientId,
                type = NotificationType.TIP_COMMENT,
                senderId = senderId,
                senderName = senderUser.nickname,
                senderProfileUrl = senderUser.profileImageUrl,
                aggregatedUserIds = listOf(senderId), // 댓글은 항상 단일 사용자
                relatedContentId = tip.id,
                tipTitle = tip.title,
                tipFirstImageUrl = firstImageUrl,
                createdAt = Timestamp.now(),
                isRead = false
            )
            db.collection("Notifications").add(newNotification).await()
            Log.d(TAG, "[TIP_COMMENT] 알림 생성 완료: ${tip.id}")

        } catch (e: Exception) {
            Log.e(TAG, "[TIP_COMMENT] 요리 팁 댓글 알림 추가 실패", e)
        }
    }

    /**
     * 요리 팁 답글 알림을 추가합니다.
     */
    suspend fun addTipReplyNotification(tip: CookingTip, reply: TipComment) {
        val recipientId = reply.recipientId ?: return // 답글 받는 사람이 없으면 종료
        val senderId = reply.userId ?: return

        if (recipientId == senderId) return // 자기 자신에게 답글 단 경우 알림 보내지 않음

        val firstImageUrl = tip.content?.firstOrNull { !it.imageUrl.isNullOrEmpty() }?.imageUrl

        try {
            val senderUser = db.collection("Users").document(senderId).get().await()
                .toObject(User::class.java) ?: User("알 수 없음", null)

            // 답글도 그룹화하지 않고 항상 새 알림을 생성합니다.
            val newNotification = Notification(
                userId = recipientId,
                type = NotificationType.TIP_REPLY,
                senderId = senderId,
                senderName = senderUser.nickname,
                senderProfileUrl = senderUser.profileImageUrl,
                aggregatedUserIds = listOf(senderId), // 답글도 항상 단일 사용자
                relatedContentId = tip.id,
                tipTitle = tip.title,
                tipFirstImageUrl = firstImageUrl,
                commentContent = reply.comment, // 답글 내용 추가
                createdAt = Timestamp.now(),
                isRead = false
            )
            db.collection("Notifications").add(newNotification).await()
            Log.d(TAG, "[TIP_REPLY] 알림 생성 완료: ${tip.id}의 댓글에 대한 답글")

        } catch (e: Exception) {
            Log.e(TAG, "[TIP_REPLY] 요리 팁 답글 알림 추가 실패", e)
        }
    }
}