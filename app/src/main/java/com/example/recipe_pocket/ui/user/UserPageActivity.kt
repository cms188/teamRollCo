package com.example.recipe_pocket.ui.user

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.bumptech.glide.Glide
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.example.recipe_pocket.ui.auth.EditProfileActivity
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.recipe.search.SearchResult
import com.example.recipe_pocket.R
import com.example.recipe_pocket.ui.review.MyReviewsActivity
import com.example.recipe_pocket.ui.tip.LikedTipsActivity
import com.example.recipe_pocket.ui.tip.MyTipsActivity
import com.example.recipe_pocket.ui.main.WriteChoiceDialogFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import utils.ToolbarUtils

class UserPageActivity : AppCompatActivity() {

    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage

    // Views
    private lateinit var profileImageView: ImageView
    private lateinit var nicknameTextView: TextView
    private lateinit var badgeTextView: TextView
    private lateinit var editInfoTextView: TextView
    private lateinit var badgeCardView: CardView
    private lateinit var myRecipesLayout: LinearLayout
    private lateinit var followersLayout: LinearLayout
    private lateinit var followingLayout: LinearLayout
    private lateinit var bottomNavigationView: BottomNavigationView

    // 신규 메뉴 레이아웃
    private lateinit var bookmarkLayout: LinearLayout
    private lateinit var likedRecipesLayout: LinearLayout
    private lateinit var reviewManageLayout: LinearLayout
    private lateinit var myTipsLayout: LinearLayout
    private lateinit var likedTipsLayout: LinearLayout
    private lateinit var recentRecipeLayout: LinearLayout

    // TextViews for counts
    private lateinit var recipeCountTextView: TextView
    private lateinit var followerCountTextView: TextView
    private lateinit var followingCountTextView: TextView

    // 이미지 픽커 결과 처리를 위한 런처
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfilePicture(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdge()
        setContentView(R.layout.activity_userpage)

        // 뷰 초기화
        initializeViews()

        // 툴바 설정
        ToolbarUtils.setupTransparentToolbar(this, "MY")

        // 클릭 리스너 설정
        setupClickListeners()

        // 하단 네비게이션 설정
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        bottomNavigationView.menu.findItem(R.id.fragment_settings).isChecked = true
    }

    private fun initializeViews() {
        // 프로필 섹션
        profileImageView = findViewById(R.id.imageView_profile)
        nicknameTextView = findViewById(R.id.textView_authorName)
        badgeTextView = findViewById(R.id.textView_badge)
        editInfoTextView = findViewById(R.id.textView_editInfo)
        badgeCardView = findViewById(R.id.cardView_badge)

        // 통계 섹션
        myRecipesLayout = findViewById(R.id.layout_myRecipes)
        followersLayout = findViewById(R.id.layout_followers)
        followingLayout = findViewById(R.id.layout_following)
        recipeCountTextView = findViewById(R.id.textView_myRecipesCount)
        followerCountTextView = findViewById(R.id.textView_followersCount)
        followingCountTextView = findViewById(R.id.textView_followingCount)

        // 활동 섹션 (ID 수정 및 추가)
        bookmarkLayout = findViewById(R.id.layout_bookmark)
        likedRecipesLayout = findViewById(R.id.layout_liked_recipes)
        reviewManageLayout = findViewById(R.id.layout_reviewManage)
        myTipsLayout = findViewById(R.id.layout_my_tips)
        likedTipsLayout = findViewById(R.id.layout_liked_tips)
        recentRecipeLayout = findViewById(R.id.layout_recentRecipe)


        // 하단 네비게이션
        bottomNavigationView = findViewById(R.id.bottom_navigation)
    }

    private fun setupToolbar() {
        // 툴바 표시 텍스트 설정
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = "MY"

        // 툴바 버튼 숨김
        val toolbarBtn = findViewById<ImageButton>(R.id.back_button)
        toolbarBtn.visibility = View.GONE

        // 툴바 상태바 높이만큼 보정
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams { height = statusBarHeight + dpToPx(56) }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun  EdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
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
                    // 닉네임 설정
                    val nickname = document.getString("nickname") ?: "닉네임 없음"
                    nicknameTextView.text = nickname

                    // [수정] 칭호 설정 로직
                    val title = document.getString("title")
                    badgeCardView.visibility = View.VISIBLE // 항상 보이도록 변경
                    if (!title.isNullOrEmpty()) {
                        badgeTextView.text = title
                    } else {
                        badgeTextView.text = "칭호를 설정해보세요"
                    }


                    // 프로필 이미지 설정
                    val imageUrl = document.getString("profileImageUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .circleCrop()
                            .into(profileImageView)
                    } else {
                        profileImageView.setImageResource(R.drawable.ic_profile_placeholder)
                    }

                    // 게시물, 팔로워, 팔로잉 수 업데이트
                    recipeCountTextView.text = "${document.getLong("recipeCount") ?: 0}"
                    followerCountTextView.text = "${document.getLong("followerCount") ?: 0}"
                    followingCountTextView.text = "${document.getLong("followingCount") ?: 0}"

                } else {
                    Toast.makeText(this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "데이터 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners() {
        // 프로필 사진 클릭 - 갤러리 열기
        profileImageView.setOnClickListener {
            openGallery()
        }

        // 정보 수정 클릭
        editInfoTextView.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // 칭호 클릭
        badgeCardView.setOnClickListener {
            startActivity(Intent(this, TitleListActivity::class.java))
        }

        // 내 레시피 클릭
        myRecipesLayout.setOnClickListener {
            startActivity(Intent(this, MyRecipesActivity::class.java))
        }

        // 팔로워 클릭
        followersLayout.setOnClickListener {
            openFollowList("followers")
        }

        // 팔로잉 클릭
        followingLayout.setOnClickListener {
            openFollowList("following")
        }

        // -----------------------

        // 북마크 클릭
        bookmarkLayout.setOnClickListener {
            startActivity(Intent(this, BookmarkActivity::class.java))
        }

        // 좋아요한 레시피 클릭
        likedRecipesLayout.setOnClickListener {
            startActivity(Intent(this, LikedRecipesActivity::class.java))
        }

        // 리뷰 관리 클릭
        reviewManageLayout.setOnClickListener {
            startActivity(Intent(this, MyReviewsActivity::class.java))
        }

        // 내가 쓴 팁 클릭
        myTipsLayout.setOnClickListener {
            startActivity(Intent(this, MyTipsActivity::class.java))
        }

        // 좋아요한 팁 클릭
        likedTipsLayout.setOnClickListener {
            startActivity(Intent(this, LikedTipsActivity::class.java))
        }


        // 최근 본 레시피 클릭
        recentRecipeLayout.setOnClickListener {
            startActivity(Intent(this, RecentlyViewedRecipesActivity::class.java))
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

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
    private fun updateProfileUrlInFirestore(url: String) {
        val currentUser = auth.currentUser ?: return

        firestore.collection("Users").document(currentUser.uid)
            .update("profileImageUrl", url)
            .addOnSuccessListener {
                Toast.makeText(this, "프로필 사진이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                // 화면에 즉시 반영
                Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(profileImageView)
            }
            .addOnFailureListener {
                Toast.makeText(this, "프로필 사진 정보 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openFollowList(mode: String) {
        val intent = Intent(this, FollowListActivity::class.java).apply {
            putExtra("USER_ID", auth.currentUser?.uid)
            putExtra("MODE", mode)
        }
        startActivity(intent)
    }
    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemReselectedListener {
        }

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.fragment_settings) {
                return@setOnItemSelectedListener true
            }

            val intent = when (item.itemId) {
                R.id.fragment_home -> Intent(this, MainActivity::class.java)
                R.id.fragment_search -> Intent(this, SearchResult::class.java)
                R.id.fragment_another -> Intent(this, BookmarkActivity::class.java)
                R.id.fragment_favorite -> {
                    if (auth.currentUser != null) {
                        WriteChoiceDialogFragment().show(supportFragmentManager, WriteChoiceDialogFragment.TAG)
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
