package com.example.recipe_pocket

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.CategoryPageBinding
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.ui.category.CategoryItem
import com.example.recipe_pocket.ui.category.CategoryGridAdapter
import com.example.recipe_pocket.ui.recipe.search.CreationPeriod
import com.example.recipe_pocket.ui.recipe.search.FilterBottomSheetFragment
import com.example.recipe_pocket.ui.recipe.search.SearchFilter
import com.example.recipe_pocket.ui.recipe.search.CookingTime
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.*

class CategoryPageActivity : AppCompatActivity(), FilterBottomSheetFragment.OnFilterAppliedListener {

    companion object {
        private const val EXTRA_INITIAL_CATEGORY = "extra_initial_category"

        fun createIntent(context: Context, category: String): Intent {
            return Intent(context, CategoryPageActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_CATEGORY, category)
            }
        }
    }

    private lateinit var binding: CategoryPageBinding
    private lateinit var recipeAdapter: RecipeAdapter
    private lateinit var categoryTypeAdapter: CategoryGridAdapter
    private lateinit var categoryDishAdapter: CategoryGridAdapter
    private lateinit var categoryThemeAdapter: CategoryGridAdapter

    private enum class SortOrder { LATEST, VIEWS, LIKES, BOOKMARKS }
    private var currentSortOrder = SortOrder.LATEST
    private var selectedCategory: String = "전체"
    private var recipeListCache = listOf<Recipe>()
    private var currentFilter = SearchFilter.default()
    private val categoryTags = mutableMapOf<String, TextView>()

    private var isAllCategoriesVisible = false

    // 분류별 카테고리
    private val categoryByType = listOf(
        CategoryItem("한식", R.drawable.cat1_1, "한식"),
        CategoryItem("양식", R.drawable.cat1_2, "양식"),
        CategoryItem("중식", R.drawable.cat1_3, "중식"),
        CategoryItem("일식", R.drawable.cat1_4, "일식"),
        CategoryItem("동남아식", R.drawable.cat1_5, "동남아식"),
        CategoryItem("남미식", R.drawable.cat1_6, "남미식"),
        CategoryItem("분식", R.drawable.cat1_7, "분식")
    )

    // 종류별 카테고리
    private val categoryByDish = listOf(
        CategoryItem("밑반찬", R.drawable.cat2_1, "밑반찬"),
        CategoryItem("국물요리", R.drawable.cat2_2, "국물요리"),
        CategoryItem("밥", R.drawable.cat2_3, "밥"),
        CategoryItem("면", R.drawable.cat2_4, "면"),
        CategoryItem("일품", R.drawable.cat2_5, "일품"),
        CategoryItem("디저트", R.drawable.cat2_6, "디저트"),
        CategoryItem("음료", R.drawable.cat2_7, "음료"),
        CategoryItem("채소요리", R.drawable.cat2_8, "채소요리"),
        CategoryItem("간편식", R.drawable.cat2_9, "간편식"),
        CategoryItem("키즈", R.drawable.cat2_10, "키즈")
    )

    // 상황별/테마 카테고리
    private val categoryByTheme = listOf(
        CategoryItem("캠핑", R.drawable.cat3_1, "캠핑"),
        CategoryItem("제철요리", R.drawable.cat3_2, "제철요리"),
        CategoryItem("초스피드", R.drawable.cat3_3, "초스피드"),
        CategoryItem("손님접대", R.drawable.cat3_4, "손님접대"),
        CategoryItem("명절", R.drawable.cat3_5, "명절")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CategoryPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val initialCategory = intent.getStringExtra(EXTRA_INITIAL_CATEGORY)
        setupBackButton()
        setupRecyclerView()
        setupCategoryTags()
        setupFilterAndSortButtons()
        setupAllCategoriesDropdown()
        handleOnBackPressed()

        val resolvedCategory = initialCategory?.takeIf { categoryTags.containsKey(it) } ?: "전체"
        selectedCategory = resolvedCategory
        updateCategoryUI(resolvedCategory)
        loadRecipesByCategory(resolvedCategory)
    }

    override fun onFilterApplied(filter: SearchFilter) {
        currentFilter = filter
        updateFilterButtonUI()
        applyFiltersAndSort()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.SearchResultLayout) { v, windowInsets ->
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

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewSearchResults.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@CategoryPageActivity)
        }
    }

    private fun setupCategoryTags() {
        // 카테고리 태그 뷰들을 맵에 저장
        categoryTags["전체"] = binding.tagAll
        categoryTags["한식"] = binding.tagKorean
        categoryTags["양식"] = binding.tagWestern
        categoryTags["중식"] = binding.tagChinese
        categoryTags["일식"] = binding.tagJapanese
        categoryTags["동남아식"] = binding.tagSoutheastAsian
        categoryTags["남미식"] = binding.tagSouthAmerican
        categoryTags["분식"] = binding.tagSnack
        categoryTags["밑반찬"] = binding.tagSideDish
        categoryTags["국물요리"] = binding.tagSoup
        categoryTags["밥"] = binding.tagRice
        categoryTags["면"] = binding.tagNoodle
        categoryTags["일품"] = binding.tagMainDish
        categoryTags["디저트"] = binding.tagDessert
        categoryTags["음료"] = binding.tagDrink
        categoryTags["채소요리"] = binding.tagVeggie
        categoryTags["간편식"] = binding.tagInstant
        categoryTags["키즈"] = binding.tagKids
        categoryTags["캠핑"] = binding.tagCamping
        categoryTags["제철요리"] = binding.tagSeasonal
        categoryTags["초스피드"] = binding.tagQuick
        categoryTags["손님접대"] = binding.tagGuest
        categoryTags["명절"] = binding.tagHoliday
        categoryTags["기타"] = binding.tagOther

        // 각 카테고리 태그에 클릭 리스너 설정
        categoryTags.forEach { (category, textView) ->
            textView.setOnClickListener {
                selectCategory(category)
            }
        }
    }

    private fun selectCategory(category: String) {
        if (selectedCategory != category) {
            selectedCategory = category
            updateCategoryUI(category)
            loadRecipesByCategory(category)

            // 드롭다운이 열려있으면 닫기
            if (isAllCategoriesVisible) {
                toggleAllCategoriesView()
            }
        }
    }

    private fun updateCategoryUI(selectedCategory: String) {
        var targetView: TextView? = null
        categoryTags.forEach { (category, textView) ->
            if (category == selectedCategory) {
                // 선택된 카테고리 스타일
                textView.setBackgroundResource(R.drawable.bg_category_tag_selected)
                textView.setTextColor(ContextCompat.getColor(this, R.color.white))
                targetView = textView
            } else {
                // 선택되지 않은 카테고리 스타일
                textView.setBackgroundResource(R.drawable.bg_category_tag_normal)
                textView.setTextColor(ContextCompat.getColor(this, R.color.black))
            }
        }
        targetView?.let { selectedView ->
            binding.categoryScrollView.post {
                val scrollView = binding.categoryScrollView
                val containerWidth = scrollView.getChildAt(0)?.width ?: scrollView.width
                val desiredScroll = selectedView.left - (scrollView.width - selectedView.width) / 2
                val maxScroll = containerWidth - scrollView.width
                val scrollX = if (maxScroll > 0) desiredScroll.coerceIn(0, maxScroll) else 0
                scrollView.smoothScrollTo(scrollX, 0)
            }
        }
    }

    private fun loadRecipesByCategory(category: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewSearchResults.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE

        lifecycleScope.launch {
            val result = if (category == "전체") {
                // 전체 카테고리일 경우 모든 레시피 로드
                RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 20)
            } else {
                // 특정 카테고리의 레시피만 로드
                RecipeLoader.loadRecipesByCategory(category)
            }

            binding.progressBar.visibility = View.GONE

            result.fold(
                onSuccess = { recipes ->
                    recipeListCache = recipes
                    applyFiltersAndSort()
                },
                onFailure = { exception ->
                    binding.tvNoResults.text = "데이터를 불러오는 중 오류가 발생했습니다."
                    binding.tvNoResults.visibility = View.VISIBLE
                    Toast.makeText(this@CategoryPageActivity,
                        "레시피 로드 실패: ${exception.message}",
                        Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun setupFilterAndSortButtons() {
        // 필터 버튼 설정
        binding.tvFilterButton.setOnClickListener {
            val bottomSheet = FilterBottomSheetFragment.newInstance(currentFilter)
            bottomSheet.show(supportFragmentManager, "FilterBottomSheet")
        }

        // 정렬 버튼 설정
        binding.tvSortButton.setOnClickListener { view ->
            showSortPopup(view)
        }
    }

    private fun showSortPopup(anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        popup.menuInflater.inflate(R.menu.sort_options, popup.menu)

        // 현재 선택된 정렬 옵션 체크
        when (currentSortOrder) {
            SortOrder.LATEST -> popup.menu.findItem(R.id.sort_latest).isChecked = true
            SortOrder.VIEWS -> popup.menu.findItem(R.id.sort_views).isChecked = true
            SortOrder.LIKES -> popup.menu.findItem(R.id.sort_likes).isChecked = true
            SortOrder.BOOKMARKS -> popup.menu.findItem(R.id.sort_bookmarks).isChecked = true
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sort_latest -> {
                    currentSortOrder = SortOrder.LATEST
                    binding.tvSortButton.text = "최신순"
                    applyFiltersAndSort()
                    true
                }
                R.id.sort_views -> {
                    currentSortOrder = SortOrder.VIEWS
                    binding.tvSortButton.text = "조회순"
                    applyFiltersAndSort()
                    true
                }
                R.id.sort_likes -> {
                    currentSortOrder = SortOrder.LIKES
                    binding.tvSortButton.text = "좋아요순"
                    applyFiltersAndSort()
                    true
                }
                R.id.sort_bookmarks -> {
                    currentSortOrder = SortOrder.BOOKMARKS
                    binding.tvSortButton.text = "북마크순"
                    applyFiltersAndSort()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun updateFilterButtonUI() {
        if (!currentFilter.isDefault()) {
            binding.tvFilterButton.setBackgroundResource(R.drawable.bg_filter_button_active)
            binding.tvFilterButton.setTextColor(ContextCompat.getColor(this, R.color.primary))
        } else {
            binding.tvFilterButton.setBackgroundResource(R.drawable.bg_gray_rounded)
            binding.tvFilterButton.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun applyFiltersAndSort() {
        // 필터링
        val filteredList = filterRecipes(recipeListCache, currentFilter)

        // 정렬
        val sortedList = when (currentSortOrder) {
            SortOrder.LATEST -> filteredList.sortedByDescending { it.createdAt }
            SortOrder.VIEWS -> filteredList.sortedByDescending { it.viewCount ?: 0 }
            SortOrder.LIKES -> filteredList.sortedByDescending { it.likeCount ?: 0 }
            SortOrder.BOOKMARKS -> filteredList.sortedByDescending { it.bookmarkedBy?.size ?: 0 }
        }

        // UI 업데이트
        if (sortedList.isNotEmpty()) {
            binding.recyclerViewSearchResults.visibility = View.VISIBLE
            binding.tvNoResults.visibility = View.GONE
            recipeAdapter.updateRecipes(sortedList)
        } else {
            val message = if (selectedCategory == "전체") {
                "조건에 맞는 레시피가 없습니다."
            } else {
                "'$selectedCategory' 카테고리에 조건에 맞는 레시피가 없습니다."
            }
            binding.tvNoResults.text = message
            binding.tvNoResults.visibility = View.VISIBLE
            binding.recyclerViewSearchResults.visibility = View.GONE
        }
    }

    private fun filterRecipes(recipes: List<Recipe>, filter: SearchFilter): List<Recipe> {
        if (filter.isDefault()) return recipes

        return recipes.filter { recipe ->
            // 작성 기간 필터
            val creationDateCheck = when (filter.creationPeriod) {
                CreationPeriod.ALL -> true
                else -> {
                    val recipeDate = recipe.createdAt?.toDate()
                    if (recipeDate == null) false else {
                        val startOfPeriod = Calendar.getInstance().apply {
                            when (filter.creationPeriod) {
                                CreationPeriod.TODAY -> {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                CreationPeriod.WEEK -> {
                                    firstDayOfWeek = Calendar.MONDAY
                                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                }
                                CreationPeriod.MONTH -> {
                                    set(Calendar.DAY_OF_MONTH, 1)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                }
                                CreationPeriod.YEAR -> {
                                    set(Calendar.DAY_OF_YEAR, 1)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                }
                                else -> {}
                            }
                        }.time
                        recipeDate.after(startOfPeriod)
                    }
                }
            }

            // 카테고리 필터
            val categoryCheck = if (filter.categories.isEmpty()) true else {
                recipe.categoryList?.any { filter.categories.contains(it) } ?: false
            }

            // 난이도 필터
            val difficultyCheck = if (filter.difficulty.isEmpty()) true else {
                filter.difficulty.contains(recipe.difficulty)
            }

            // 조리 시간 필터
            val timeCheck = when (filter.cookingTime) {
                CookingTime.ALL -> true
                CookingTime.UNDER_10 -> (recipe.cookingTime ?: Int.MAX_VALUE) <= 10
                CookingTime.UNDER_20 -> (recipe.cookingTime ?: Int.MAX_VALUE) <= 20
                CookingTime.UNDER_30 -> (recipe.cookingTime ?: Int.MAX_VALUE) <= 30
            }

            creationDateCheck && categoryCheck && difficultyCheck && timeCheck
        }
    }

    private fun setupAllCategoriesDropdown() {
        // 분류별 섹션 어댑터 설정
        categoryTypeAdapter = CategoryGridAdapter(categoryByType) { item ->
            selectCategory(item.categoryKey)
        }
        binding.recyclerViewCategoryType.apply {
            layoutManager = GridLayoutManager(this@CategoryPageActivity, 5)
            adapter = categoryTypeAdapter
        }

        // 종류별 섹션 어댑터 설정
        categoryDishAdapter = CategoryGridAdapter(categoryByDish) { item ->
            selectCategory(item.categoryKey)
        }
        binding.recyclerViewCategoryDish.apply {
            layoutManager = GridLayoutManager(this@CategoryPageActivity, 5)
            adapter = categoryDishAdapter
        }

        // 상황별/테마 섹션 어댑터 설정
        categoryThemeAdapter = CategoryGridAdapter(categoryByTheme) { item ->
            selectCategory(item.categoryKey)
        }
        binding.recyclerViewCategoryTheme.apply {
            layoutManager = GridLayoutManager(this@CategoryPageActivity, 5)
            adapter = categoryThemeAdapter
        }

        binding.btnCategoryAll.setOnClickListener {
            toggleAllCategoriesView()
        }
    }

    private fun toggleAllCategoriesView() {
        isAllCategoriesVisible = !isAllCategoriesVisible
        if (isAllCategoriesVisible) {
            binding.allCategoriesContainer.apply {
                bringToFront()
                alpha = 0f
                translationY = -height.toFloat()
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .start()
            }
            binding.btnCategoryAll.animate().rotation(180f).setDuration(300).start()
        } else {
            binding.allCategoriesContainer.animate()
                .alpha(0f)
                .translationY(-binding.allCategoriesContainer.height.toFloat())
                .setDuration(300)
                .withEndAction {
                    binding.allCategoriesContainer.visibility = View.GONE
                }
                .start()
            binding.btnCategoryAll.animate().rotation(0f).setDuration(300).start()
        }
    }

    private fun handleOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAllCategoriesVisible) {
                    toggleAllCategoriesView()
                } else {
                    finish()
                }
            }
        })
    }
}