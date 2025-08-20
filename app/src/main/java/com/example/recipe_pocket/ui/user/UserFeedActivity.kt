package com.example.recipe_pocket.ui.user

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.repository.NotificationHandler
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.databinding.ActivityUserFeedBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class UserFeedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserFeedBinding
    private lateinit var recipeAdapter: RecipeAdapter
    private var targetUserId: String? = null
    private var isFollowing = false

    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser

    companion object {
        const val EXTRA_USER_ID = "USER_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetUserId = intent.getStringExtra(EXTRA_USER_ID)
        if (targetUserId == null) {
            finish()
            return
        }

        setupRecyclerView()
        setupClickListeners()
        loadAllData()
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewUserRecipes.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewUserRecipes.adapter = recipeAdapter
    }

    private fun setupClickListeners() {
        binding.ivBackButton.setOnClickListener { finish() }
        binding.btnFollow.setOnClickListener { toggleFollow() }
    }

    private fun loadAllData() {
        // 사용자 정보, 팔로우 상태, 레시피 목록을 순차적으로 로드
        lifecycleScope.launch {
            loadUserInfo()
            checkIfFollowing()
            loadUserRecipes()
        }
    }

    private fun loadUserInfo() {
        db.collection("Users").document(targetUserId!!).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val nickname = document.getString("nickname") ?: "사용자"
                // 프로필 영역 닉네임 설정
                binding.tvUserNickname.text = nickname

                // 칭호 설정
                val title = document.getString("title")
                if (!title.isNullOrEmpty()) {
                    binding.tvUserTitle.visibility = View.VISIBLE
                    binding.tvUserTitle.text = title
                } else {
                    binding.tvUserTitle.visibility = View.GONE
                }

                // 팔로워 정보 텍스트 설정
                val recipeCount = document.getLong("recipeCount") ?: 0
                val followerCount = document.getLong("followerCount") ?: 0
                val followingCount = document.getLong("followingCount") ?: 0
                binding.tvFollowerInfo.text = "게시물 $recipeCount · 팔로워 $followerCount · 팔로잉 $followingCount"

                // 프로필 이미지 로드
                val imageUrl = document.getString("profileImageUrl")
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this@UserFeedActivity).load(imageUrl).into(binding.ivProfilePicture)
                } else {
                    binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                }
            }
        }
    }

    private fun loadUserRecipes() {
        lifecycleScope.launch {
            val result = RecipeLoader.loadRecipesByUserId(targetUserId!!)
            result.onSuccess { recipes ->
                recipeAdapter.updateRecipes(recipes)
            }
        }
    }

    private fun checkIfFollowing() {
        // 자기 자신인 경우 팔로우 버튼 숨김
        if (currentUser == null || currentUser.uid == targetUserId) {
            binding.btnFollow.visibility = View.GONE
            return
        }
        binding.btnFollow.visibility = View.VISIBLE

        // 현재 유저가 이 유저를 팔로우하고 있는지 확인
        db.collection("Users").document(currentUser.uid)
            .collection("following").document(targetUserId!!)
            .get().addOnSuccessListener { document ->
                isFollowing = document.exists()
                updateFollowButton()
            }
    }

    private fun updateFollowButton() {
        // isFollowing 상태에 따라 버튼의 텍스트와 스타일 변경
        if (isFollowing) {
            binding.btnFollow.text = "언팔로우"
            binding.btnFollow.background = ContextCompat.getDrawable(this, R.drawable.bg_gray_rounded)
            binding.btnFollow.setTextColor(ContextCompat.getColor(this, R.color.black))
        } else {
            binding.btnFollow.text = "팔로우"
            binding.btnFollow.background = ContextCompat.getDrawable(this, R.drawable.bg_orange_button_rounded)
            binding.btnFollow.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }

    private fun toggleFollow() {
        if (currentUser == null) return

        val myFollowingRef = db.collection("Users").document(currentUser.uid).collection("following").document(targetUserId!!)
        val targetFollowerRef = db.collection("Users").document(targetUserId!!).collection("followers").document(currentUser.uid)
        val myUserDocRef = db.collection("Users").document(currentUser.uid)
        val targetUserDocRef = db.collection("Users").document(targetUserId!!)

        // 트랜잭션을 사용하여 여러 문서를 안전하게 업데이트
        db.runTransaction { transaction ->
            if (isFollowing) { // 언팔로우 로직
                transaction.delete(myFollowingRef)
                transaction.delete(targetFollowerRef)
                transaction.update(myUserDocRef, "followingCount", FieldValue.increment(-1))
                transaction.update(targetUserDocRef, "followerCount", FieldValue.increment(-1))
            } else { // 팔로우 로직
                transaction.set(myFollowingRef, mapOf("followedAt" to FieldValue.serverTimestamp()))
                transaction.set(targetFollowerRef, mapOf("followedAt" to FieldValue.serverTimestamp()))
                transaction.update(myUserDocRef, "followingCount", FieldValue.increment(1))
                transaction.update(targetUserDocRef, "followerCount", FieldValue.increment(1))
            }
        }.addOnSuccessListener {
            val wasFollowing = isFollowing
            isFollowing = !isFollowing

            // 새로 팔로우 했을 때만 알림 생성
            if (!wasFollowing) {
                lifecycleScope.launch {
                    NotificationHandler.createOrUpdateFollowNotification(
                        recipientId = targetUserId!!,
                        senderId = currentUser.uid
                    )
                }
            }

            updateFollowButton()
            loadUserInfo() // 팔로워 수 즉시 갱신
        }
    }
}