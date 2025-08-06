package com.example.recipe_pocket.ui.user

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.example.recipe_pocket.R
import com.example.recipe_pocket.databinding.ActivityUserpageBinding
import com.example.recipe_pocket.ui.auth.EditProfileActivity
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.recipe.search.SearchResult
import com.example.recipe_pocket.ui.recipe.write.CookWrite01Activity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class UserPageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserpageBinding
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage

    // 이미지 픽커 결과 처리를 위한 런처
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfilePicture(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserpageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        binding.bottomNavigationView.menu.findItem(R.id.fragment_settings).isChecked = true
    }

    /**
     * Firestore에서 현재 로그인된 사용자의 데이터를 불러와 UI에 적용하는 함수
     */
    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        firestore.collection("Users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val nickname = document.getString("nickname") ?: "닉네임 없음"
                    binding.tvNickname.text = nickname

                    val title = document.getString("title")
                    if (!title.isNullOrEmpty()) {
                        binding.tvTitleBadge.visibility = View.VISIBLE
                        binding.tvTitleBadge.text = title
                    } else {
                        binding.tvTitleBadge.visibility = View.GONE
                    }

                    val imageUrl = document.getString("profileImageUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(imageUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(binding.ivProfilePicture)
                    } else {
                        binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                    }

                    // ### 이 부분이 빠져있었습니다! ###
                    // 게시물, 팔로워, 팔로잉 수 UI 업데이트
                    binding.tvRecipeCountClickable.text = "게시물\n${document.getLong("recipeCount") ?: 0}"
                    binding.tvFollowerCountClickable.text = "팔로워\n${document.getLong("followerCount") ?: 0}"
                    binding.tvFollowingCountClickable.text = "팔로잉\n${document.getLong("followingCount") ?: 0}"

                } else {
                    // 사용자 문서가 없는 경우의 처리
                }
            }
            .addOnFailureListener {
                // 데이터 로드 실패 시 처리
            }
    }

    private fun setupClickListeners() {
        binding.ivBackButton.setOnClickListener { finish() }

        // 프로필 사진 클릭 리스너
        binding.ivProfilePicture.setOnClickListener {
            openGallery()
        }

        // 게시물, 팔로워, 팔로잉 텍스트 클릭 리스너
        binding.tvRecipeCountClickable.setOnClickListener {
            startActivity(Intent(this, MyRecipesActivity::class.java))
        }
        binding.tvFollowerCountClickable.setOnClickListener {
            openFollowList("followers")
        }
        binding.tvFollowingCountClickable.setOnClickListener {
            openFollowList("following")
        }

        // 그리드 메뉴 클릭 리스너
        binding.menuMyRecipes.setOnClickListener {
            startActivity(Intent(this, MyRecipesActivity::class.java))
        }
        binding.menuMyReviews.setOnClickListener {
            startActivity(Intent(this, com.example.recipe_pocket.ui.review.MyReviewsActivity::class.java))
        }
        // '내 좋아요' 메뉴 클릭 리스너
        binding.menuMyLikes.setOnClickListener {
            startActivity(Intent(this, LikedRecipesActivity::class.java))
        }
        binding.menuBookmarks.setOnClickListener {
            startActivity(Intent(this, BookmarkActivity::class.java))
        }
        binding.menuTitles.setOnClickListener {
            startActivity(Intent(this, TitleListActivity::class.java))
        }
        binding.menuEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    /**
     * 갤러리를 열어 이미지를 선택하는 함수
     */
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    /**
     * 선택한 이미지를 Firebase Storage에 업로드하는 함수
     */
    private fun uploadProfilePicture(imageUri: Uri) {
        val currentUser = auth.currentUser ?: return
        val storageRef = storage.reference.child("profile_pictures/${currentUser.uid}")
        Toast.makeText(this, "프로필 사진을 업로드 중입니다...", Toast.LENGTH_SHORT).show()
        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    updateProfileUrlInFirestore(uri.toString())
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Storage에 업로드된 이미지 URL을 Firestore 사용자 문서에 저장하는 함수
     */
    private fun updateProfileUrlInFirestore(url: String) {
        val currentUser = auth.currentUser ?: return
        firestore.collection("Users").document(currentUser.uid)
            .update("profileImageUrl", url)
            .addOnSuccessListener {
                Toast.makeText(this, "프로필 사진이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                // 화면에 즉시 반영
                Glide.with(this).load(url).into(binding.ivProfilePicture)
            }
            .addOnFailureListener {
                Toast.makeText(this, "프로필 사진 정보 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * 팔로워/팔로잉 목록 액티비티를 여는 함수
     * @param mode "followers" 또는 "following"을 전달하여 모드를 구분
     */
    private fun openFollowList(mode: String) {
        val intent = Intent(this, FollowListActivity::class.java).apply {
            putExtra("USER_ID", auth.currentUser?.uid)
            putExtra("MODE", mode)
        }
        startActivity(intent)
    }

    /**
     * 하단 네비게이션 메뉴의 동작을 설정하는 함수
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemReselectedListener { /* 아무것도 하지 않음 */ }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.fragment_settings) {
                return@setOnItemSelectedListener true
            }

            val intent = when (item.itemId) {
                R.id.fragment_home -> Intent(this, MainActivity::class.java)
                R.id.fragment_search -> Intent(this, SearchResult::class.java)
                R.id.fragment_another -> Intent(this, BookmarkActivity::class.java)
                R.id.fragment_favorite -> {
                    if(auth.currentUser != null) {
                        startActivity(Intent(this, CookWrite01Activity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    return@setOnItemSelectedListener true
                }
                else -> null
            }

            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(it)
                overridePendingTransition(0, 0)
            }
            true
        }
    }
}