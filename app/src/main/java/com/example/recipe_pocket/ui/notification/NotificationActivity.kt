package com.example.recipe_pocket.ui.notification

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.data.Notification
import com.example.recipe_pocket.data.NotificationType
import com.example.recipe_pocket.databinding.ActivityNotificationBinding
import com.example.recipe_pocket.ui.recipe.read.RecipeDetailActivity
import com.example.recipe_pocket.ui.user.TitleListActivity
import com.example.recipe_pocket.ui.user.UserFeedActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding
    private lateinit var newNotificationAdapter: NotificationAdapter
    private lateinit var readNotificationAdapter: NotificationAdapter

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("NotificationDebug", "NotificationActivity: onCreate")
        setupToolbar()
        setupRecyclerViews()
    }

    override fun onResume() {
        super.onResume()
        Log.d("NotificationDebug", "NotificationActivity: onResume - 데이터 로드 시작")
        loadNotifications()
    }

    override fun onPause() {
        super.onPause()
        Log.d("NotificationDebug", "NotificationActivity: onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d("NotificationDebug", "NotificationActivity: onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("NotificationDebug", "NotificationActivity: onDestroy")
    }

    private fun setupToolbar() {
        binding.ivBackButton.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK)
        super.onBackPressed()
    }

    private fun setupRecyclerViews() {
        newNotificationAdapter = NotificationAdapter(true) { notification -> onNotificationClick(notification) }
        readNotificationAdapter = NotificationAdapter(false) { notification -> onNotificationClick(notification) }

        binding.recyclerViewNewNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            adapter = newNotificationAdapter
        }

        binding.recyclerViewReadNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            adapter = readNotificationAdapter
        }
    }

    // --- ▼▼▼ 이 함수 전체를 교체합니다 ▼▼▼ ---
    private fun loadNotifications() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // (생략)
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        Log.d("NotificationDebug", "NotificationActivity: 서버에서 데이터 가져오기 시작...")

        db.collection("Notifications")
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                if (documents == null) {
                    // (생략)
                    return@addOnSuccessListener
                }

                // --- 수동 매핑 시작 ---
                val allNotifications = documents.mapNotNull { doc ->
                    try {
                        // 각 필드를 직접 Map에서 꺼내서 Notification 객체를 생성합니다.
                        val data = doc.data
                        Notification(
                            id = doc.id,
                            userId = data["userId"] as? String,
                            type = (data["type"] as? String)?.let { NotificationType.valueOf(it) },
                            senderId = data["senderId"] as? String,
                            senderName = data["senderName"] as? String,
                            senderProfileUrl = data["senderProfileUrl"] as? String,
                            aggregatedUserIds = data["aggregatedUserIds"] as? List<String>,
                            relatedContentId = data["relatedContentId"] as? String,
                            recipeTitle = data["recipeTitle"] as? String,
                            recipeThumbnailUrl = data["recipeThumbnailUrl"] as? String,
                            titleName = data["titleName"] as? String,
                            createdAt = data["createdAt"] as? Timestamp,
                            // isRead 필드를 Boolean으로 직접 캐스팅합니다. 기본값은 false.
                            isRead = data["isRead"] as? Boolean ?: false
                        )
                    } catch (e: Exception) {
                        Log.e("NotificationDebug", "데이터 매핑 중 오류 발생: doc ID=${doc.id}", e)
                        null // 오류 발생 시 해당 문서는 무시
                    }
                }
                // --- 수동 매핑 종료 ---

                Log.d("NotificationDebug", "NotificationActivity: 서버에서 총 ${allNotifications.size}개 알림 수동 매핑 완료.")

                val newNotifications = allNotifications.filter { !it.isRead }
                val readNotifications = allNotifications.filter { it.isRead }
                Log.d("NotificationDebug", "NotificationActivity: 필터링 결과 -> 새로운 알림 ${newNotifications.size}개, 읽은 알림 ${readNotifications.size}개")

                updateUiWithNotifications(newNotifications, readNotifications)

                if (newNotifications.isNotEmpty()) {
                    markNotificationsAsRead(newNotifications)
                }

            }.addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("NotificationDebug", "NotificationActivity: 알림 로드 실패", e)
            }
    }
    // --- ▲▲▲ 이 함수 전체를 교체합니다 ▲▲▲ ---

    private fun updateUiWithNotifications(newNotifs: List<Notification>, readNotifs: List<Notification>) {
        if (newNotifs.isNotEmpty()) {
            binding.tvNewNotificationHeader.visibility = View.VISIBLE
            binding.recyclerViewNewNotifications.visibility = View.VISIBLE
            newNotificationAdapter.submitList(newNotifs)
        } else {
            binding.tvNewNotificationHeader.visibility = View.GONE
            binding.recyclerViewNewNotifications.visibility = View.GONE
        }

        if (readNotifs.isNotEmpty()) {
            binding.tvReadNotificationHeader.visibility = View.VISIBLE
            binding.recyclerViewReadNotifications.visibility = View.VISIBLE
            readNotificationAdapter.submitList(readNotifs)
        } else {
            binding.tvReadNotificationHeader.visibility = View.GONE
            binding.recyclerViewReadNotifications.visibility = View.GONE
        }

        if (newNotifs.isEmpty() && readNotifs.isEmpty()) {
            binding.tvReadNotificationHeader.text = "알림이 없습니다."
            binding.tvReadNotificationHeader.visibility = View.VISIBLE
        }
    }


    private fun onNotificationClick(notification: Notification) {
        val intent = when(notification.type) {
            NotificationType.LIKE, NotificationType.REVIEW -> Intent(this, RecipeDetailActivity::class.java).apply { putExtra("RECIPE_ID", notification.relatedContentId) }
            NotificationType.FOLLOW -> Intent(this, UserFeedActivity::class.java).apply { putExtra(UserFeedActivity.EXTRA_USER_ID, notification.relatedContentId) }
            NotificationType.TITLE -> Intent(this, TitleListActivity::class.java)
            else -> null
        }
        intent?.let { startActivity(it) }
    }

    private fun markNotificationsAsRead(notificationsToMark: List<Notification>) {
        if (notificationsToMark.isEmpty()) return

        val batch = db.batch()
        val idsToUpdate = notificationsToMark.mapNotNull { it.id }
        Log.d("NotificationDebug", "NotificationActivity: [${idsToUpdate.joinToString()}] ID를 가진 ${idsToUpdate.size}개의 알림을 '읽음'으로 처리 요청합니다.")

        for (id in idsToUpdate) {
            val docRef = db.collection("Notifications").document(id)
            batch.update(docRef, "isRead", true)
        }

        batch.commit().addOnSuccessListener {
            Log.d("NotificationDebug", "NotificationActivity: DB '읽음' 처리 성공.")
        }.addOnFailureListener { e ->
            Log.e("NotificationDebug", "NotificationActivity: DB '읽음' 처리 실패", e)
        }
    }
}