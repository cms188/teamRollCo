package com.example.recipe_pocket.ui.notification

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.Notification
import com.example.recipe_pocket.data.NotificationType
import com.example.recipe_pocket.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NotificationAdapter(
    private val isNew: Boolean,
    private val onItemClick: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private var notifications: List<Notification> = emptyList()

    fun submitList(newNotifications: List<Notification>) {
        this.notifications = newNotifications
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(notifications[position])
    }

    override fun getItemCount(): Int = notifications.size

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notification: Notification) {
            val context = binding.root.context

            if (isNew) {
                binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.light_orange_bg))
            } else {
                binding.root.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            }

            val count = notification.aggregatedUserIds?.size ?: 1
            var title = ""
            var content = ""

            when (notification.type) {
                NotificationType.LIKE -> {
                    title = notification.recipeTitle ?: "내 레시피"
                    content = if (count > 1) {
                        "${notification.senderName}님 외 ${count - 1}명이 회원님의 게시글을 좋아합니다."
                    } else {
                        "${notification.senderName}님이 회원님의 게시글을 좋아합니다."
                    }
                    loadRecipeImage(notification.recipeThumbnailUrl)
                }
                NotificationType.REVIEW -> {
                    title = notification.recipeTitle ?: "내 레시피"
                    content = if (count > 1) {
                        "${notification.senderName}님 외 ${count - 1}명이 회원님의 게시글에 리뷰를 남겼습니다."
                    } else {
                        "${notification.senderName}님이 회원님의 게시글에 리뷰를 남겼습니다."
                    }
                    loadRecipeImage(notification.recipeThumbnailUrl)
                }
                NotificationType.FOLLOW -> {
                    title = if (count > 1) {
                        "${notification.senderName}님 외 ${count - 1}명이 회원님을 팔로우합니다."
                    } else {
                        "${notification.senderName}님이 회원님을 팔로우합니다."
                    }
                    content = "프로필을 눌러 확인해보세요!"
                    loadProfileImage(notification.senderProfileUrl)
                }
                NotificationType.TITLE -> {
                    title = "'${notification.titleName}' 칭호를 획득했습니다!"
                    content = when(notification.titleName) {
                        "새싹 요리사" -> "게시글을 3회 작성하여 칭호를 획득했습니다."
                        "우리집 요리사" -> "게시글을 10회 작성하여 칭호를 획득했습니다."
                        "요리 장인" -> "게시글을 25회 작성하여 칭호를 획득했습니다."
                        "레시피 마스터" -> "게시글을 50회 작성하여 칭호를 획득했습니다."
                        else -> "새로운 칭호를 획득했습니다."
                    }
                    binding.ivNotificationImage.visibility = View.GONE
                }
                NotificationType.TIP_LIKE -> {
                    title = notification.tipTitle ?: "내 요리 팁"
                    content = if (count > 1) {
                        "${notification.senderName}님 외 ${count - 1}명이 회원님의 요리 팁을 추천했습니다."
                    } else {
                        "${notification.senderName}님이 회원님의 요리 팁을 추천했습니다."
                    }
                    loadRecipeImage(notification.tipFirstImageUrl)
                }
                NotificationType.TIP_COMMENT -> {
                    title = notification.tipTitle ?: "내 요리 팁"
                    content = if (count > 1) {
                        "${notification.senderName}님 외 ${count - 1}명이 댓글을 남겼습니다."
                    } else {
                        "${notification.senderName}님이 댓글을 남겼습니다."
                    }
                    loadRecipeImage(notification.tipFirstImageUrl)
                }
                NotificationType.TIP_REPLY -> { // 답글 알림 처리
                    title = "내 댓글에 답글이 달렸습니다."
                    content = "${notification.senderName}: \"${notification.commentContent}\""
                    loadRecipeImage(notification.tipFirstImageUrl)
                }
                null -> {}
            }

            binding.tvNotificationTitle.text = title
            binding.tvNotificationContent.text = content
            binding.tvNotificationTime.text = formatTimestamp(notification.createdAt?.toDate())

            binding.root.setOnClickListener {
                onItemClick(notification)
            }
        }

        private fun loadRecipeImage(url: String?) {
            binding.ivNotificationImage.visibility = View.VISIBLE
            Glide.with(binding.root.context)
                .load(url)
                .apply(RequestOptions.bitmapTransform(RoundedCorners(16)))
                .placeholder(R.drawable.bg_no_img_gray)
                .error(R.drawable.bg_no_img_gray)
                .into(binding.ivNotificationImage)
        }

        private fun loadProfileImage(url: String?) {
            binding.ivNotificationImage.visibility = View.VISIBLE
            Glide.with(binding.root.context)
                .load(url)
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(binding.ivNotificationImage)
        }

        private fun formatTimestamp(date: Date?): String {
            if (date == null) return ""
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Seoul")
            }
            return sdf.format(date)
        }
    }
}