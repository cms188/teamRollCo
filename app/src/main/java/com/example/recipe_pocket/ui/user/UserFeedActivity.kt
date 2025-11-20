package com.example.recipe_pocket.ui.user

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.databinding.ActivityUserFeedBinding
import com.example.recipe_pocket.repository.NotificationHandler
import com.example.recipe_pocket.repository.RecipeLoader
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
        utils.ToolbarUtils.setupTransparentToolbar(this, "")
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
        lifecycleScope.launch {
            try {
                val userDoc = db.collection("Users").document(targetUserId!!).get().await()
                if (userDoc.exists()) {
                    val nickname = userDoc.getString("nickname") ?: "사용자"
                    val title = userDoc.getString("title")
                    val imageUrl = userDoc.getString("profileImageUrl")

                    // 레시피 수 실시간 집계
                    val recipesQuery = db.collection("Recipes")
                        .whereEqualTo("userId", targetUserId!!).get().await()
                    val recipeCount = recipesQuery.size()

                    // 팔로워 수 실시간 집계 (존재하는 사용자만 카운트)
                    val followersSnapshot = db.collection("Users").document(targetUserId!!)
                        .collection("followers").get().await()
                    val validFollowers = followersSnapshot.documents.filter {
                        db.collection("Users").document(it.id).get().await().exists()
                    }
                    val followerCount = validFollowers.size

                    // 팔로잉 수 실시간 집계 (존재하는 사용자만 카운트)
                    val followingSnapshot = db.collection("Users").document(targetUserId!!)
                        .collection("following").get().await()
                    val validFollowing = followingSnapshot.documents.filter {
                        db.collection("Users").document(it.id).get().await().exists()
                    }
                    val followingCount = validFollowing.size

                    withContext(Dispatchers.Main) {
                        // 프로필 영역 닉네임 설정
                        binding.tvUserNickname.text = nickname

                        // 칭호 설정
                        if (!title.isNullOrEmpty()) {
                            binding.tvUserTitle.visibility = View.VISIBLE
                            binding.tvUserTitle.text = title
                        } else {
                            binding.tvUserTitle.visibility = View.GONE
                        }

                        // 통계 정보 텍스트 설정
                        binding.tvPostCount.text = recipeCount.toString()
                        binding.tvFollowerCount.text = followerCount.toString()
                        binding.tvFollowingCount.text = followingCount.toString()

                        // 프로필 이미지 로드
                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(this@UserFeedActivity).load(imageUrl)
                                .into(binding.ivProfilePicture)
                        } else {
                            binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@UserFeedActivity, "사용자 정보 로딩 실패", Toast.LENGTH_SHORT)
                        .show()
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
            binding.btnFollow.setBackgroundColor(
                ContextCompat.getColor(this, R.color.accent_honey_yellow)
            )  // 회색 배경
            binding.btnFollow.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        } else {
            binding.btnFollow.text = "팔로우"
            binding.btnFollow.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))  // 주황색 배경
            binding.btnFollow.setTextColor(ContextCompat.getColor(this, R.color.primary_cream))
        }
    }

    private fun toggleFollow() {
        if (currentUser == null) return

        val myFollowingRef = db.collection("Users").document(currentUser.uid)
            .collection("following").document(targetUserId!!)
        val targetFollowerRef = db.collection("Users").document(targetUserId!!)
            .collection("followers").document(currentUser.uid)
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
                transaction.set(
                    targetFollowerRef,
                    mapOf("followedAt" to FieldValue.serverTimestamp())
                )
                transaction.update(myUserDocRef, "followingCount", FieldValue.increment(1))
                transaction.update(targetUserDocRef, "followerCount", FieldValue.increment(1))
            }
        }.addOnSuccessListener {
            val wasFollowing = isFollowing
            isFollowing = !isFollowing

            // 알림 생성/삭제 로직
            lifecycleScope.launch {
                if (isFollowing) {
                    // 새로 팔로우 했을 때 'add' 함수 호출
                    NotificationHandler.addFollowNotification(
                        recipientId = targetUserId!!,
                        senderId = currentUser.uid
                    )
                } else {
                    // 언팔로우 했을 때 'remove' 함수 호출
                    NotificationHandler.removeFollowNotification(
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