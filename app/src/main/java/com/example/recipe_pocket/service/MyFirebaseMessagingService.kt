package com.example.recipe_pocket.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.recipe_pocket.R
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_Service"
        private const val CHANNEL_ID = "recipe_pocket_notification_channel"
    }

    // 새 토큰이 생성될 때마다 호출됩니다. (앱 설치, 데이터 삭제 등)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "새로운 FCM 토큰 발급: $token")
        // 로그인 상태라면 새 토큰을 서버(Firestore)에 저장합니다.
        sendRegistrationToServer(token)
    }

    // FCM 메시지를 수신했을 때 호출됩니다. (앱이 포그라운드에 있을 때)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM 메시지 수신: From: ${remoteMessage.from}")

        // 알림 페이로드가 있는지 확인
        remoteMessage.notification?.let {
            Log.d(TAG, "알림 제목: ${it.title}")
            Log.d(TAG, "알림 본문: ${it.body}")
            showNotification(it.title, it.body)
        }
    }

    // 수신한 메시지를 실제 기기 알림으로 표시하는 함수
    private fun showNotification(title: String?, body: String?) {
        createNotificationChannel()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name) // 알림 아이콘 (아래에서 추가 설명)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // Android 13 (API 33) 이상에서는 알림 권한이 필요합니다.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "알림 권한이 없어 알림을 표시할 수 없습니다.")
            return
        }

        with(NotificationManagerCompat.from(this)) {
            // notificationId는 각 알림을 식별하는 고유한 Int 값입니다.
            val notificationId = System.currentTimeMillis().toInt()
            notify(notificationId, builder.build())
        }
    }

    // 알림을 표시하기 위한 '채널'을 생성하는 함수 (Android 8.0 이상 필수)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "레시피 포켓 알림"
            val descriptionText = "레시피에 대한 좋아요, 리뷰, 팔로우 등 새로운 소식을 알려줍니다."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 새로운 FCM 토큰을 Firestore의 사용자 문서에 저장하는 함수
    private fun sendRegistrationToServer(token: String?) {
        if (token == null) return
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null) {
            val userDocRef = Firebase.firestore.collection("Users").document(currentUser.uid)
            // 여러 기기에서 로그인할 수 있으므로, 토큰을 배열에 추가/관리하는 것이 좋습니다.
            userDocRef.update("fcmTokens", FieldValue.arrayUnion(token))
                .addOnSuccessListener { Log.d(TAG, "FCM 토큰이 Firestore에 성공적으로 업데이트되었습니다.") }
                .addOnFailureListener { e -> Log.w(TAG, "FCM 토큰 업데이트 실패", e) }
        }
    }
}