package com.example.recipe_pocket

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ItemUserBinding // ViewBinding import
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

// UserAdapter의 생성자 인자 타입을 명확히 함
class UserAdapter(
    private var userList: List<UserAdapter.UserWithId>,
    private val onFollowClick: (user: UserAdapter.UserWithId, position: Int) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    // 데이터 클래스는 변경 없음
    data class UserWithId(
        val id: String,
        val nickname: String?,
        val profileImageUrl: String?,
        var isFollowing: Boolean = false
    )

    // ViewHolder가 ItemUserBinding을 직접 받도록 수정
    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: UserWithId) {
            val context = binding.root.context
            // binding 객체를 통해 UI 요소에 접근
            binding.tvNickname.text = user.nickname ?: "이름 없음"

            if (!user.profileImageUrl.isNullOrEmpty()) {
                Glide.with(context).load(user.profileImageUrl).into(binding.ivProfile)
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_profile_placeholder)
            }

            // 팔로우 버튼 설정
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

            // 아이템 클릭 시 해당 유저의 피드로 이동
            binding.root.setOnClickListener {
                val intent = Intent(context, UserFeedActivity::class.java).apply {
                    putExtra(UserFeedActivity.EXTRA_USER_ID, user.id)
                }
                context.startActivity(intent)
            }
        }

        // 팔로우 버튼 UI 업데이트 함수
        fun updateFollowButton(isFollowing: Boolean) {
            if (isFollowing) {
                binding.btnFollowToggle.text = "언팔로우"
                binding.btnFollowToggle.background = ContextCompat.getDrawable(binding.root.context, R.drawable.bg_gray_rounded)
                binding.btnFollowToggle.setTextColor(ContextCompat.getColor(binding.root.context, R.color.black))
            } else {
                binding.btnFollowToggle.text = "팔로우"
                binding.btnFollowToggle.background = ContextCompat.getDrawable(binding.root.context, R.drawable.bg_orange_button_rounded)
                binding.btnFollowToggle.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        // LayoutInflater를 통해 binding 객체 생성
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userList[position])
    }

    override fun getItemCount(): Int = userList.size

    // 데이터 업데이트 함수 (변경 없음)
    fun updateUserList(newList: List<UserWithId>) {
        this.userList = newList
        notifyDataSetChanged()
    }
}