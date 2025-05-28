package com.example.recipe_pocket

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ActivityMainBinding // 생성된 메인 액티비티 바인딩 클래스
import com.example.recipe_pocket.databinding.CookCard01Binding   // 생성된 cook_card_01 바인딩 클래스
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // 메인 액티비티 바인딩 객체

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge 처리 (main_frame_layout ID가 activity_main.xml의 루트 레이아웃 ID라고 가정)
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainFrameLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> { // MarginLayoutParams으로 캐스팅
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                // topMargin = insets.top // 필요시 상단 마진도 조절
            }
            WindowInsetsCompat.CONSUMED
        }

        // 검색 버튼 클릭 리스너
        binding.topSearchButton.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }

        // 레시피 카드 초기화
        try {
            // ActivityMainBinding에 hotCook1, hotCook2, hotCook3 프로퍼티가
            // CookCard01Binding 타입으로 생성되었다고 가정
            binding.hotCook1?.let { setupRecipeCardWithBinding(it, "hot_cook_1") }
            binding.hotCook2?.let { setupRecipeCardWithBinding(it, "hot_cook_2") }
            binding.hotCook3?.let { setupRecipeCardWithBinding(it, "hot_cook_3") }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "레시피 카드 초기화 중 오류 발생: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        // ViewPager2 설정 (cook_tips_view_pager ID가 activity_main.xml에 있다고 가정)
        val cookTipItems = listOf(
            CookTipItem("오늘의 추천 요리팁!", "재료 손질부터 플레이팅까지", R.drawable.testimg1),
            CookTipItem("간단한 밑반찬 만들기", "냉장고를 든든하게 채워요", R.drawable.testimg2),
            CookTipItem("특별한 날 홈파티 메뉴", "쉽고 근사하게 준비하기", R.drawable.testimg1)
        )
        val cookTipAdapter = CookTipAdapter(cookTipItems) // CookTipAdapter 및 CookTipItem 클래스 필요
        binding.cookTipsViewPager.adapter = cookTipAdapter
        binding.cookTipsViewPager.offscreenPageLimit = 1

        // BottomNavigationView 설정 (bottom_navigation_view ID가 activity_main.xml에 있다고 가정)
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.fragment_home -> {
                    // 현재 액티비티이므로 새로 시작할 필요 없음 (또는 새로고침 로직 추가)
                    // val intent = Intent(this, MainActivity::class.java)
                    // intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    // startActivity(intent)
                    true
                }
                R.id.fragment_search -> {
                    val intent = Intent(this, SearchActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.fragment_favorite -> { // 레시피 작성 (또는 즐겨찾기)
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        val intent = Intent(this, RecipeWriteActivity::class.java)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                    }
                    true
                }
                R.id.fragment_another -> { // 이 ID는 실제 기능에 맞게 이름 변경 권장
                    Toast.makeText(this, "찜 목록 (준비중)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.fragment_settings -> { // 내 정보 (또는 설정)
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        val intent = Intent(this, UserPageActivity::class.java)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }
        // 기본 선택 아이템 설정 (선택 사항)
        // binding.bottomNavigationView.selectedItemId = R.id.fragment_home
    }

    /**
     * CookCard01Binding 객체를 받아 레시피 카드 UI를 설정하고 데이터를 로드하는 private 멤버 함수
     */
    private fun setupRecipeCardWithBinding(
        cardBinding: CookCard01Binding, // cook_card_01.xml에 대한 바인딩 객체
        cardName: String
    ) {
        // 데이터 로드 및 UI 업데이트 함수 호출 (View 객체를 전달)
        loadAndDisplayRecipe(
            context = this,
            recipeImageView = cardBinding.recipeImageView,
            titleTextView = cardBinding.recipeNameText,
            cookingTimeTextView = cardBinding.cookingTimeText,
            authorProfileImageView = cardBinding.authorProfileImage,
            authorNameTextView = cardBinding.authorNameText,
            cardNameForToast = cardName
            // 플레이스홀더 및 에러 이미지는 loadAndDisplayRecipe 함수의 기본값 사용
        )
    }

    /**
     * 랜덤 레시피 데이터를 로드하고, 제공된 UI 요소에 해당 데이터를 표시하는 private 멤버 함수
     */
    private fun loadAndDisplayRecipe(
        context: Context,
        recipeImageView: ImageView,
        titleTextView: TextView,
        cookingTimeTextView: TextView,
        authorProfileImageView: ImageView,
        authorNameTextView: TextView,
        cardNameForToast: String,
        recipeImagePlaceholderResId: Int = R.drawable.bg_main_rounded_gray, // 기본 플레이스홀더
        recipeImageErrorResId: Int = R.drawable.testimg1, // 기본 에러 이미지 (변경 권장)
        profileImagePlaceholderResId: Int = R.drawable.bg_main_circle_gray, // 프로필 기본 플레이스홀더
        profileImageErrorResId: Int = R.drawable.testimg1 // 프로필 기본 에러 (변경 권장, 예: R.drawable.ic_default_profile)
    ) {
        RecipeLoader.loadRandomRecipeWithAuthor(
            context = context,
            onSuccess = { recipe ->
                titleTextView.text = recipe.title ?: "제목 없음"
                cookingTimeTextView.text = recipe.cookingTime?.let { "${it}분" } ?: "시간 정보 없음"

                val author = recipe.author
                if (author != null) {
                    authorNameTextView.text = author.nickname ?: "작성자 정보 없음"
                    author.profileImageUrl?.let { url ->
                        if (url.isNotEmpty()) {
                            Glide.with(context)
                                .load(url)
                                .placeholder(profileImagePlaceholderResId)
                                .error(profileImageErrorResId)
                                .circleCrop()
                                .into(authorProfileImageView)
                        } else {
                            authorProfileImageView.setImageResource(profileImageErrorResId)
                        }
                    } ?: run {
                        authorProfileImageView.setImageResource(profileImageErrorResId)
                    }
                } else {
                    authorNameTextView.text = "작성자 미상"
                    authorProfileImageView.setImageResource(profileImageErrorResId)
                }
            },
            onFailure = { exception ->
                Toast.makeText(context, "$cardNameForToast 데이터 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                // 실패 시 UI 초기화 (선택 사항)
                titleTextView.text = "정보 없음"
                cookingTimeTextView.text = ""
                authorNameTextView.text = ""
                recipeImageView.setImageResource(recipeImageErrorResId)
                authorProfileImageView.setImageResource(profileImageErrorResId)
            },
            imageViewToLoad = recipeImageView,
            placeholderResId = recipeImagePlaceholderResId,
            errorResId = recipeImageErrorResId
        )
    }
}