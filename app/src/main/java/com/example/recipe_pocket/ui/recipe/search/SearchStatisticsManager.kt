package com.example.recipe_pocket.ui.recipe.search

import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SearchStatisticsManager {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private const val COLLECTION_NAME = "search_statistics"

    /**
     * 사용자가 검색한 검색어를 Firestore에 기록하고 카운트를 증가시킵니다.
     * 하루에 한 사용자당 동일 검색어는 1번만 카운트됩니다.
     * @param term 사용자가 입력한 검색어
     */
    suspend fun updateSearchTerm(term: String) {
        val currentUser = auth.currentUser ?: return // 비로그인 사용자는 집계하지 않음
        if (term.isBlank()) return

        val normalizedTerm = term.lowercase().trim()
        val docRef = db.collection(COLLECTION_NAME).document(normalizedTerm)

        // 오늘 날짜를 "yyyyMMdd" 형식의 문자열로 생성
        val todayDateString = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
        // 오늘 날짜와 사용자 UID를 조합한 고유 키 생성
        val dailyUserKey = "${todayDateString}_${currentUser.uid}"

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    // 문서가 존재하면, 오늘 이 유저가 검색했는지 확인
                    val dailySearchedBy = snapshot.get("searchedBy_daily") as? List<String> ?: emptyList()

                    if (!dailySearchedBy.contains(dailyUserKey)) {
                        // 오늘 처음 검색한 경우: count 증가, dailyUserKey 추가, 시간 업데이트
                        transaction.update(docRef, "count", FieldValue.increment(1))
                        transaction.update(docRef, "searchedBy_daily", FieldValue.arrayUnion(dailyUserKey))
                        transaction.update(docRef, "lastSearched", FieldValue.serverTimestamp())
                    }
                    // 이미 오늘 검색했다면 아무 작업도 하지 않음
                } else {
                    // 문서가 없으면 새로 생성
                    val newData = hashMapOf(
                        "keyword" to normalizedTerm,
                        "count" to 1,
                        "lastSearched" to FieldValue.serverTimestamp(),
                        "searchedBy_daily" to listOf(dailyUserKey) // 첫 검색 기록 추가
                    )
                    transaction.set(docRef, newData)
                }
                null
            }.await()
        } catch (e: Exception) {
            System.err.println("검색어 통계 업데이트 실패: ${e.message}")
        }
    }

    /**
     * 최근 일주일간 검색된 검색어 중 가장 인기 있는 상위 5개를 가져옵니다.
     */
    suspend fun getPopularSearches(): Result<List<String>> {
        return try {
            // 일주일 전의 타임스탬프를 계산
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            val oneWeekAgo = Timestamp(calendar.time)

            // [수정] 쿼리 구조 변경
            // Firestore 쿼리: 먼저 'lastSearched'로 최신 100개 정도의 문서를 가져옴
            val querySnapshot = db.collection(COLLECTION_NAME)
                .whereGreaterThan("lastSearched", oneWeekAgo)
                .orderBy("lastSearched", Query.Direction.DESCENDING) // lastSearched로 정렬
                .limit(100) // 너무 많은 데이터를 가져오지 않도록 적당히 제한
                .get()
                .await()

            // 가져온 데이터를 count 기준으로 내림차순 정렬하고 상위 5개를 선택
            val popularSearches = querySnapshot.documents
                .sortedByDescending { it.getLong("count") ?: 0L } // count 필드로 정렬
                .take(5) // 상위 5개 선택
                .mapNotNull { it.getString("keyword") } // keyword 필드만 추출

            Result.success(popularSearches)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}