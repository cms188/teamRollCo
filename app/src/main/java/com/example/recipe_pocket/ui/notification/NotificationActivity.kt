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
import com.example.recipe_pocket.ui.tip.CookTipDetailActivity
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
        utils.ToolbarUtils.setupTransparentToolbar(this, "알림", navigateToMainActivity = true)
        setContentView(binding.root)
        setupRecyclerViews()
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
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

    private fun loadNotifications() {
        val currentUser = auth.currentUser ?: return
        binding.progressBar.visibility = View.VISIBLE

        db.collection("Notifications")
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { documents ->
                binding.progressBar.visibility = View.GONE
                if (documents == null) return@addOnSuccessListener

                val allNotifications = documents.toObjects(Notification::class.java)
                val newNotifications = allNotifications.filter { !it.isRead }
                val readNotifications = allNotifications.filter { it.isRead }

                updateUiWithNotifications(newNotifications, readNotifications)
                if (newNotifications.isNotEmpty()) {
                    markNotificationsAsRead(newNotifications)
                }

            }.addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("NotificationDebug", "NotificationActivity: 알림 로드 실패", e)
            }
    }

    private fun updateUiWithNotifications(newNotifs: List<Notification>, readNotifs: List<Notification>) {
        binding.tvNewNotificationHeader.visibility = if (newNotifs.isNotEmpty()) View.VISIBLE else View.GONE
        binding.recyclerViewNewNotifications.visibility = if (newNotifs.isNotEmpty()) View.VISIBLE else View.GONE
        newNotificationAdapter.submitList(newNotifs)

        binding.tvReadNotificationHeader.visibility = if (readNotifs.isNotEmpty()) View.VISIBLE else View.GONE
        binding.recyclerViewReadNotifications.visibility = if (readNotifs.isNotEmpty()) View.VISIBLE else View.GONE
        readNotificationAdapter.submitList(readNotifs)

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
            NotificationType.TIP_LIKE, NotificationType.TIP_COMMENT, NotificationType.TIP_REPLY -> Intent(this, CookTipDetailActivity::class.java).apply { putExtra(CookTipDetailActivity.EXTRA_TIP_ID, notification.relatedContentId) }
            else -> null
        }
        intent?.let { startActivity(it) }
    }

    private fun markNotificationsAsRead(notificationsToMark: List<Notification>) {
        val batch = db.batch()
        notificationsToMark.mapNotNull { it.id }.forEach { id ->
            val docRef = db.collection("Notifications").document(id)
            batch.update(docRef, "isRead", true)
        }
        batch.commit()
    }
}