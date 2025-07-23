/*
package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

class SearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_result) // activity_main.xml 또는 사용 중인 메인 레이아웃 파일
        //https://developer.android.com/develop/ui/views/layout/edge-to-edge?hl=ko#kotlin
        //동작 모드 또는 버튼 모드에서 시각적 겹침을 방지
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainFrameLayout)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                topMargin = insets.top //상단도 마찬가지로 겹침 방지. 꼭 필요한 것은 아님
            }
            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
        var backButton: ImageView = findViewById<ImageView>(R.id.iv_back_button)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

    }
}*/


/*백업용 mainactivity.kt

package com.example.recipe_pocket

import android.content.Context // 사용하지 않으면 제거
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
import androidx.lifecycle.lifecycleScope // 코루틴 사용을 위해 필요
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ActivityMainBinding
import com.example.recipe_pocket.databinding.CookCard01Binding
import com.google.firebase.auth.FirebaseAuth

import kotlinx.coroutines.launch // 코루틴 사용을 위해 필요

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 기본 이미지 리소스 ID (실제 프로젝트의 리소스 ID로 변경하세요)
    private val defaultRecipeImagePlaceholderResId: Int = R.drawable.bg_main_rounded_gray
    private val defaultRecipeImageErrorResId: Int = R.drawable.testimg1 // 에러 시 표시할 기본 이미지
    private val defaultProfileImagePlaceholderResId: Int = R.drawable.bg_main_circle_gray
    private val defaultProfileImageErrorResId: Int = R.drawable.testimg1 // 프로필 에러 시 기본 이미지 (예: R.drawable.ic_default_profile)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainFrameLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        // 검색 버튼 클릭 리스너
        binding.topSearchButton.setOnClickListener {
            val intent = Intent(this, SearchResult::class.java)
            startActivity(intent)
        }

        // 레시피 카드 초기화 (코루틴 사용)
        loadCookRecipes()

        // ViewPager2 설정 (CookTipItem, CookTipAdapter 클래스가 정의되어 있다고 가정)
        val cookTipItems = listOf(
            CookTipItem("오늘의 추천 요리팁!", "재료 손질부터 플레이팅까지", R.drawable.testimg1),
            CookTipItem("간단한 밑반찬 만들기", "냉장고를 든든하게 채워요", R.drawable.testimg2),
            CookTipItem("특별한 날 홈파티 메뉴", "쉽고 근사하게 준비하기", R.drawable.testimg1)
        )
        val cookTipAdapter = CookTipAdapter(cookTipItems)
        binding.cookTipsViewPager.adapter = cookTipAdapter
        binding.cookTipsViewPager.offscreenPageLimit = 1

        // BottomNavigationView 설정
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.fragment_home -> {
                    true
                }
                R.id.fragment_search -> {
                    startActivity(Intent(this, SearchResult::class.java))
                    true
                }
                R.id.fragment_favorite -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        startActivity(Intent(this, RecipeWriteActivity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    true
                }
                R.id.fragment_another -> {
                    Toast.makeText(this, "찜 목록 SSZZZ(준비중)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.fragment_settings -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        startActivity(Intent(this, UserPageActivity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    true
                }
                else -> false
            }
        }
    }
    //카드에 db내용 추가
    private fun loadCookRecipes() {
        // lifecycleScope.launch를 사용하여 UI 관련 코루틴을 시작합니다.
        lifecycleScope.launch {
            // RecipeLoader에서 3개의 레시피를 요청합니다.
            val result = RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 3)

            result.fold(
                onSuccess = { recipes ->
                    // 성공적으로 레시피 목록을 가져왔을 때
                    val hotCookCardBindings = listOfNotNull(
                        binding.hotCook1,
                        binding.hotCook2,
                        binding.hotCook3,
                    )

                    if (recipes.isEmpty()) {
                        Toast.makeText(this@MainActivity, "표시할 인기 레시피가 없습니다.", Toast.LENGTH_LONG).show()
                        // 모든 카드를 기본 상태로 설정
                        hotCookCardBindings.forEachIndexed { index, cardBinding ->
                            clearRecipeCard(cardBinding, "hot_cook_${index + 1}")
                        }
                        return@fold
                    }

                    // 가져온 레시피를 각 카드에 순서대로 채웁니다.
                    recipes.forEachIndexed { index, recipe ->
                        if (index < hotCookCardBindings.size) {
                            // 해당 카드에 레시피 정보를 표시합니다.
                            displayRecipeInCard(
                                cardBinding = hotCookCardBindings[index],
                                recipe = recipe,
                                cardNameForToast = "hot_cook_${index + 1}"
                            )
                        }
                    }

                    // 만약 가져온 레시피 수가 카드 수보다 적다면, 나머지 카드는 비우거나 기본 상태로 둡니다.
                    if (recipes.size < hotCookCardBindings.size) {
                        for (i in recipes.size until hotCookCardBindings.size) {
                            clearRecipeCard(hotCookCardBindings[i], "hot_cook_${i + 1}")
                        }
                    }
                },
                onFailure = { exception ->
                    // 레시피 로드에 실패했을 때
                    Toast.makeText(this@MainActivity, "인기 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    // 모든 카드를 에러 상태로 설정
                    val hotCookCardBindings = listOfNotNull(binding.hotCook1, binding.hotCook2, binding.hotCook3)
                    hotCookCardBindings.forEachIndexed { index, cardBinding ->
                        clearRecipeCard(cardBinding, "hot_cook_${index + 1}", isError = true)
                    }
                }
            )
        }
    }

    /**
     * Recipe 객체 데이터를 받아 카드 UI에 표시하는 함수
     */
    private fun displayRecipeInCard(
        cardBinding: CookCard01Binding,
        recipe: Recipe,
        cardNameForToast: String // 현재는 사용되지 않지만, 디버깅이나 로깅에 유용할 수 있음
    ) {
        // 레시피 제목 설정
        cardBinding.recipeNameText.text = recipe.title ?: "제목 없음"
        // 요리 시간 설정
        cardBinding.cookingTimeText.text = recipe.cookingTime?.let { "${it}분" } ?: "시간 정보 없음"

        // 레시피 썸네일 이미지 로드 (Glide 사용)
        recipe.thumbnailUrl?.let { url ->
            if (url.isNotEmpty()) {
                Glide.with(this@MainActivity)
                    .load(url)
                    .placeholder(defaultRecipeImagePlaceholderResId)
                    .error(defaultRecipeImageErrorResId)
                    .into(cardBinding.recipeImageView)
            } else {
                cardBinding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
            }
        } ?: run {
            cardBinding.recipeImageView.setImageResource(defaultRecipeImageErrorResId) // URL이 null인 경우
        }

        // 작성자 정보 표시
        val author = recipe.author
        if (author != null) {
            cardBinding.authorNameText.text = author.nickname ?: "작성자 정보 없음"
            author.profileImageUrl?.let { url ->
                if (url.isNotEmpty()) {
                    Glide.with(this@MainActivity)
                        .load(url)
                        .placeholder(defaultProfileImagePlaceholderResId)
                        .error(defaultProfileImageErrorResId)
                        .circleCrop() // 프로필 이미지는 원형으로
                        .into(cardBinding.authorProfileImage)
                } else {
                    cardBinding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
                }
            } ?: run {
                cardBinding.authorProfileImage.setImageResource(defaultProfileImageErrorResId) // URL이 null인 경우
            }
        } else {
            // 작성자 정보가 없는 경우 (예: recipe.userId가 없거나, User 문서 로드 실패)
            cardBinding.authorNameText.text = "작성자 미상"
            cardBinding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
        }
        // --- 클릭 리스너 설정 ---
        cardBinding.root.setOnClickListener {
            // recipe.id는 @DocumentId 어노테이션을 통해 Firestore 문서 ID로 채워집니다.
            recipe.id?.let { recipeId ->
                if (recipeId.isNotEmpty()) {
                    val intent = Intent(this@MainActivity, RecipeDetailActivity::class.java)
                    // "RECIPE_ID"라는 키로 레시피의 문서 ID를 전달합니다.
                    intent.putExtra("RECIPE_ID", recipeId)
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "레시피 정보를 불러올 수 없습니다. (ID 없음)", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this@MainActivity, "레시피 정보를 불러올 수 없습니다. (ID null)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 레시피 카드 내용을 비우거나 에러 상태로 설정하는 함수
     */
    private fun clearRecipeCard(
        cardBinding: CookCard01Binding?,
        cardName: String, // 디버깅용
        isError: Boolean = false
    ) {
        cardBinding?.let {
            it.recipeNameText.text = if (isError) "정보 없음" else "레시피 없음"
            it.cookingTimeText.text = ""
            it.authorNameText.text = "정보 없음"
            it.recipeImageView.setImageResource(if (isError) defaultRecipeImageErrorResId else defaultRecipeImagePlaceholderResId)
            it.authorProfileImage.setImageResource(if (isError) defaultProfileImageErrorResId else defaultProfileImagePlaceholderResId)
            // if (isError) Toast.makeText(this, "$cardName 데이터 로드 실패", Toast.LENGTH_SHORT).show()
            // else Toast.makeText(this, "$cardName 데이터 없음", Toast.LENGTH_SHORT).show()
        }
    }
}*/


/* 백업용 activity_main.xml
<FrameLayout
android:layout_width="wrap_content"
android:layout_height="wrap_content">

<include
android:id="@+id/hot_cook_1"
layout="@layout/cook_card_01" />
</FrameLayout>

<FrameLayout
android:layout_width="wrap_content"
android:layout_height="wrap_content">
<include layout="@layout/cook_card_01"
android:id="@+id/hot_cook_2"/>
</FrameLayout>

<FrameLayout
android:layout_width="wrap_content"
android:layout_height="wrap_content">
<include layout="@layout/cook_card_01"
android:id="@+id/hot_cook_3"/>
</FrameLayout>*/
