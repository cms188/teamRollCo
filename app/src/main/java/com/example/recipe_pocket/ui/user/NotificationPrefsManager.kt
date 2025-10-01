package com.example.recipe_pocket.ui.user

import android.content.Context
import android.content.SharedPreferences
import com.example.recipe_pocket.data.NotificationType

object NotificationPrefsManager {

    private const val PREFS_NAME = "notification_prefs"
    private const val KEY_PREFIX = "notif_enabled_"
    private const val KEY_ALL_NOTIFICATIONS = "notif_enabled_all" // 전체 알림 키

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 전체 알림의 활성화 상태를 확인합니다.
     */
    fun isAllNotificationsEnabled(context: Context): Boolean {
        val prefs = getSharedPreferences(context)
        return prefs.getBoolean(KEY_ALL_NOTIFICATIONS, true)
    }

    /**
     * 전체 알림의 활성화 상태를 저장합니다.
     */
    fun setAllNotificationsEnabled(context: Context, isEnabled: Boolean) {
        val prefs = getSharedPreferences(context)
        prefs.edit().putBoolean(KEY_ALL_NOTIFICATIONS, isEnabled).apply()
    }


    /**
     * 특정 알림 타입의 활성화 상태를 확인합니다.
     * @param context Context
     * @param type 확인할 NotificationType
     * @return 활성화 여부 (기본값: true)
     */
    fun isNotificationEnabled(context: Context, type: NotificationType): Boolean {
        if (!isAllNotificationsEnabled(context)) {
            return false
        }

        val prefs = getSharedPreferences(context)
        val key = KEY_PREFIX + type.name
        return prefs.getBoolean(key, true) // 기본적으로 모든 알림을 켜진 상태로 시작
    }

    /**
     * 특정 알림 타입의 활성화 상태를 저장합니다.
     * @param context Context
     * @param type 설정할 NotificationType
     * @param isEnabled 활성화할지 여부
     */
    fun setNotificationEnabled(context: Context, type: NotificationType, isEnabled: Boolean) {
        val prefs = getSharedPreferences(context)
        val key = KEY_PREFIX + type.name
        prefs.edit().putBoolean(key, isEnabled).apply()
    }
}