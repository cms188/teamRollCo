package com.example.recipe_pocket.ui.user

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.databinding.ItemUserBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class UserAdapter(
    private var userList: List<UserWithId>,
    private val onFollowClick: (user: UserWithId, position: Int) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    data class UserWithId(
        val id: String,
        val nickname: String?,
        val profileImageUrl: String?,
        var isFollowing: Boolean = false
    )

    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: UserWithId) {
            val context = binding.root.context
            binding.tvNickname.text = user.nickname ?: "이름 없음"

            if (!user.profileImageUrl.isNullOrEmpty()) {
                Glide.with(context).load(user.profileImageUrl).into(binding.ivProfile)
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_profile_placeholder)
            }

            val currentUser = Firebase.auth.currentUser
            if (currentUser != null && currentUser.uid != user.id) {
                binding.btnFollowToggle.visibility = View.VISIBLE
                updateFollowButton(user.isFollowing)
                binding.btnFollowToggle.setOnClickListener {
                    onFollowClick(user, adapterPosition)
                }
            } else {
                binding.btnFollowToggle.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                val intent = Intent(context, UserFeedActivity::class.java).apply {
                    putExtra(UserFeedActivity.EXTRA_USER_ID, user.id)
                }
                context.startActivity(intent)
            }
        }

        fun updateFollowButton(isFollowing: Boolean) {
            val context = binding.root.context
            if (isFollowing) {
                // 언팔로우 상태
                binding.btnFollowToggle.text = "언팔로우"
                binding.btnFollowToggle.backgroundTintList = ContextCompat.getColorStateList(context, R.color.white)
                binding.btnFollowToggle.setTextColor(ContextCompat.getColor(context, R.color.primary_variant))
                binding.btnFollowToggle.strokeColor = ContextCompat.getColorStateList(context, R.color.primary_variant)
                binding.btnFollowToggle.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.chip_stroke_width)
            } else {
                // 팔로우 상태
                binding.btnFollowToggle.text = "팔로우"
                binding.btnFollowToggle.backgroundTintList = ContextCompat.getColorStateList(context, R.color.primary)
                binding.btnFollowToggle.setTextColor(ContextCompat.getColor(context, R.color.white))
                binding.btnFollowToggle.strokeWidth = 0
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userList[position])
    }

    override fun getItemCount(): Int = userList.size

    fun updateUserList(newList: List<UserWithId>) {
        this.userList = newList
        notifyDataSetChanged()
    }
}