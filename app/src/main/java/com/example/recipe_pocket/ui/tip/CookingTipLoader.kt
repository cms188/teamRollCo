package com.example.recipe_pocket.repository

import com.example.recipe_pocket.data.CookingTip
import com.example.recipe_pocket.data.User
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

object CookingTipLoader {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // 단일 요리 팁 문서를 CookingTip 객체로 변환하고 작성자 정보를 채웁니다.
    private suspend fun enrichTipWithAuthor(document: DocumentSnapshot): CookingTip? {
        val tip = document.toObject(CookingTip::class.java) ?: return null
        tip.id = document.id

        // 현재 사용자의 추천/비추천 상태 확인
        auth.currentUser?.uid?.let { currentUserId ->
            tip.isLiked = tip.likedBy?.contains(currentUserId) == true
            tip.isDisliked = tip.dislikedBy?.contains(currentUserId) == true
        }

        // 댓글 수 가져오기
        try {
            val commentsSnapshot = db.collection("CookingTips").document(tip.id!!).collection("Comments").get().await()
            tip.commentCount = commentsSnapshot.size()
        } catch (e: Exception) {
            tip.commentCount = 0
        }

        // 작성자 정보 가져오기
        tip.userId?.let { authorId ->
            if (authorId.isNotEmpty()) {
                try {
                    val userDoc = db.collection("Users").document(authorId).get().await()
                    if (userDoc.exists()) {
                        tip.author = userDoc.toObject(User::class.java)
                    }
                } catch (e: Exception) {
                    // 오류 처리
                }
            }
        }
        return tip
    }

    // 추천 점수가 -2 이상인 요리 팁을 랜덤으로 가져옵니다.
    suspend fun loadRandomTips(count: Int): Result<List<CookingTip>> {
        return try {
            val querySnapshot = db.collection("CookingTips")
                .whereGreaterThanOrEqualTo("recommendationScore", -2)
                .get()
                .await()

            if (querySnapshot.isEmpty) return Result.success(emptyList())

            val randomDocuments = querySnapshot.documents.shuffled().take(count)

            val tips = coroutineScope {
                randomDocuments.map { doc ->
                    async { enrichTipWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(tips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 모든 요리 팁을 최신순으로 가져옵니다.
    suspend fun loadAllTips(): Result<List<CookingTip>> {
        return try {
            val querySnapshot = db.collection("CookingTips")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val tips = coroutineScope {
                querySnapshot.documents.map { doc ->
                    async { enrichTipWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(tips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 특정 사용자가 작성한 모든 요리 팁을 가져옵니다.
    suspend fun loadTipsByUserId(userId: String): Result<List<CookingTip>> {
        return try {
            val querySnapshot = db.collection("CookingTips")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val tips = coroutineScope {
                querySnapshot.documents.map { doc ->
                    async { enrichTipWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(tips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 현재 사용자가 추천한 모든 요리 팁을 가져옵니다.
    suspend fun loadLikedTips(): Result<List<CookingTip>> {
        val currentUserId = auth.currentUser?.uid ?: return Result.success(emptyList())
        return try {
            val querySnapshot = db.collection("CookingTips")
                .whereArrayContains("likedBy", currentUserId)
                .get()
                .await()

            val tips = coroutineScope {
                querySnapshot.documents.map { doc ->
                    async { enrichTipWithAuthor(doc) }
                }.awaitAll().filterNotNull()
            }
            Result.success(tips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}