package com.example.recipe_pocket.ui.user

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.example.recipe_pocket.data.NotificationType
import com.example.recipe_pocket.databinding.ActivityNotificationSettingsBinding
import utils.ToolbarUtils

class NotificationSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ToolbarUtils.setupTransparentToolbar(this, "알림 설정")
        loadSettings()
        setupListeners()
    }

    // 저장된 설정 값을 불러와 스위치 상태에 반영
    private fun loadSettings() {
        // 전체 알림 설정 로드
        val allEnabled = NotificationPrefsManager.isAllNotificationsEnabled(this)
        binding.switchAllNotifications.isChecked = allEnabled

        // 개별 알림 설정 로드
        binding.switchRecipeLikes.isChecked = NotificationPrefsManager.isNotificationEnabled(this, NotificationType.LIKE)
        binding.switchRecipeReviews.isChecked = NotificationPrefsManager.isNotificationEnabled(this, NotificationType.REVIEW)
        binding.switchFollows.isChecked = NotificationPrefsManager.isNotificationEnabled(this, NotificationType.FOLLOW)
        binding.switchTitles.isChecked = NotificationPrefsManager.isNotificationEnabled(this, NotificationType.TITLE)
        binding.switchTipLikes.isChecked = NotificationPrefsManager.isNotificationEnabled(this, NotificationType.TIP_LIKE)
        binding.switchTipComments.isChecked = NotificationPrefsManager.isNotificationEnabled(this, NotificationType.TIP_COMMENT)
        binding.switchTipReplies.isChecked = NotificationPrefsManager.isNotificationEnabled(this, NotificationType.TIP_REPLY)

        // 전체 알림 상태에 따라 하위 UI 활성화/비활성화
        updateIndividualSettingsState(allEnabled)
    }

    // 각 스위치의 상태 변경 리스너 설정
    private fun setupListeners() {
        // 전체 알림 스위치 리스너
        binding.switchAllNotifications.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefsManager.setAllNotificationsEnabled(this, isChecked)
            updateIndividualSettingsState(isChecked)

            // 모든 개별 스위치 상태를 마스터 스위치와 동기화하고 저장
            val allSwitches = listOf(
                binding.switchRecipeLikes to NotificationType.LIKE,
                binding.switchRecipeReviews to NotificationType.REVIEW,
                binding.switchFollows to NotificationType.FOLLOW,
                binding.switchTitles to NotificationType.TITLE,
                binding.switchTipLikes to NotificationType.TIP_LIKE,
                binding.switchTipComments to NotificationType.TIP_COMMENT,
                binding.switchTipReplies to NotificationType.TIP_REPLY
            )
            allSwitches.forEach { (switch, type) ->
                switch.isChecked = isChecked
                NotificationPrefsManager.setNotificationEnabled(this, type, isChecked)
            }
        }

        // 개별 알림 스위치 리스너
        binding.switchRecipeLikes.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefsManager.setNotificationEnabled(this, NotificationType.LIKE, isChecked)
        }
        binding.switchRecipeReviews.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefsManager.setNotificationEnabled(this, NotificationType.REVIEW, isChecked)
        }
        binding.switchFollows.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefsManager.setNotificationEnabled(this, NotificationType.FOLLOW, isChecked)
        }
        binding.switchTitles.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefsManager.setNotificationEnabled(this, NotificationType.TITLE, isChecked)
        }
        binding.switchTipLikes.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefsManager.setNotificationEnabled(this, NotificationType.TIP_LIKE, isChecked)
        }
        binding.switchTipComments.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefsManager.setNotificationEnabled(this, NotificationType.TIP_COMMENT, isChecked)
        }
        binding.switchTipReplies.setOnCheckedChangeListener { _, isChecked ->
            NotificationPrefsManager.setNotificationEnabled(this, NotificationType.TIP_REPLY, isChecked)
        }
    }

    // 개별 알림 설정 UI의 활성화/비활성화 상태를 업데이트하는 함수
    private fun updateIndividualSettingsState(isEnabled: Boolean) {
        binding.individualSettingsContainer.alpha = if (isEnabled) 1.0f else 0.5f
        // 컨테이너 내의 모든 자식 뷰(스위치, 텍스트뷰 등)를 재귀적으로 활성화/비활성화
        setEnabledRecursively(binding.individualSettingsContainer, isEnabled)
    }

    // ViewGroup 내의 모든 뷰를 재귀적으로 활성화/비활성화하는 함수
    private fun setEnabledRecursively(viewGroup: ViewGroup, isEnabled: Boolean) {
        viewGroup.children.forEach { child ->
            child.isEnabled = isEnabled
            if (child is ViewGroup) {
                setEnabledRecursively(child, isEnabled)
            }
        }
    }
}