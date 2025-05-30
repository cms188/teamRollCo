package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.CookCard03Binding
import com.example.recipe_pocket.databinding.SearchResultBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SearchResult : AppCompatActivity() {

    private lateinit var binding: SearchResultBinding

    // 기본 이미지 리소스 ID (실제 프로젝트의 리소스 ID로 변경하세요)
    //private val defaultRecipeImagePlaceholderResId: Int = R.drawable.bg_main_rounded_gray
    private val defaultRecipeImageErrorResId: Int = R.drawable.testimg1 // 에러 시 표시할 기본 이미지
    private val defaultProfileImagePlaceholderResId: Int = R.drawable.bg_main_circle_gray
    private val defaultProfileImageErrorResId: Int = R.drawable.testimg1 // 프로필 에러 시 기본 이미지 (예: R.drawable.ic_default_profile)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding 초기화
        binding = SearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge 처리
        // WindowCompat.setDecorFitsSystemWindows(window, false) // 전체 화면을 사용하도록 설정 (필요한 경우)
        ViewCompat.setOnApplyWindowInsetsListener(binding.SearchResultLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                // topMargin = insets.top // 주석 처리: 상단 UI가 이미 존재하므로, 이 값을 적용하면 전체가 밀릴 수 있습니다.
                // 필요시 상태바 높이만큼 특정 뷰에 패딩을 주는 방식 등을 고려해야 합니다.
                bottomMargin = insets.bottom // 하단 시스템 네비게이션 바와 BottomNavigationView 겹침 방지
                rightMargin = insets.right
            }

            // 예시: 만약 ScrollView 상단에 시스템 바 만큼의 패딩을 주고 싶다면
            // binding.scrollViewId.setPadding(binding.scrollViewId.paddingLeft, insets.top, binding.scrollViewId.paddingRight, binding.scrollViewId.paddingBottom)
            // 여기서 'scrollViewId'는 search_result.xml의 ScrollView에 ID를 부여했을 경우의 예시입니다. (현재는 ID 없음)

            WindowInsetsCompat.CONSUMED
        }
        var backButton: ImageView = findViewById<ImageView>(R.id.iv_back_button)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        loadCookRecipes()
        // 여기에 검색 결과 로드, 어댑터 설정, 필터/정렬 버튼 리스너 등
        // 추가적인 UI 초기화 및 로직을 구현합니다.
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
                        binding.searchResult1,
                        binding.searchResult2,
                        binding.searchResult3,
                        binding.searchResult4,
                        binding.searchResult5,
                        binding.searchResult6,
                    )

                    if (recipes.isEmpty()) {
                        Toast.makeText(this@SearchResult, "표시할 인기 레시피가 없습니다.", Toast.LENGTH_LONG).show()
                        // 모든 카드를 기본 상태로 설정
                        hotCookCardBindings.forEachIndexed { index, cardBinding ->
                            clearRecipeCard(cardBinding, "search_Result${index + 1}")
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
                                cardNameForToast = "search_Result${index + 1}"
                            )
                        }
                    }

                    // 만약 가져온 레시피 수가 카드 수보다 적다면, 나머지 카드는 비우거나 기본 상태로 둡니다.
                    if (recipes.size < hotCookCardBindings.size) {
                        for (i in recipes.size until hotCookCardBindings.size) {
                            clearRecipeCard(hotCookCardBindings[i], "search_Result${i + 1}")
                        }
                    }
                },
                onFailure = { exception ->
                    // 레시피 로드에 실패했을 때
                    Toast.makeText(this@SearchResult, "인기 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    // 모든 카드를 에러 상태로 설정
                    val hotCookCardBindings = listOfNotNull(binding.searchResult1, binding.searchResult2, binding.searchResult3, binding.searchResult4, binding.searchResult5, binding.searchResult6)
                    hotCookCardBindings.forEachIndexed { index, cardBinding ->
                        clearRecipeCard(cardBinding, "hot_cook_1${index + 1}", isError = true)
                    }
                }
            )
        }
    }

    /**
     * Recipe 객체 데이터를 받아 카드 UI에 표시하는 함수
     */
    private fun displayRecipeInCard(
        cardBinding: CookCard03Binding,
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
                Glide.with(this@SearchResult)
                    .load(url)
                    //.placeholder(defaultRecipeImagePlaceholderResId)
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
                    Glide.with(this@SearchResult)
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
    }

    /**
     * 레시피 카드 내용을 비우거나 에러 상태로 설정하는 함수
     */
    private fun clearRecipeCard(
        cardBinding: CookCard03Binding?,
        cardName: String, // 디버깅용
        isError: Boolean = false
    ) {
        cardBinding?.let {
            it.recipeNameText.text = if (isError) "정보 없음" else "레시피 없음"
            it.cookingTimeText.text = ""
            it.authorNameText.text = "정보 없음"
            //it.recipeImageView.setImageResource(if (isError) defaultRecipeImageErrorResId else defaultRecipeImagePlaceholderResId)
            it.authorProfileImage.setImageResource(if (isError) defaultProfileImageErrorResId else defaultProfileImagePlaceholderResId)
            // if (isError) Toast.makeText(this, "$cardName 데이터 로드 실패", Toast.LENGTH_SHORT).show()
            // else Toast.makeText(this, "$cardName 데이터 없음", Toast.LENGTH_SHORT).show()
        }
    }
}