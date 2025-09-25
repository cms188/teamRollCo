package com.example.recipe_pocket

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.CategoryPageBinding
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.recipe.search.CreationPeriod
import com.example.recipe_pocket.ui.recipe.search.FilterBottomSheetFragment
import com.example.recipe_pocket.ui.recipe.search.SearchFilter
import com.example.recipe_pocket.ui.recipe.search.SearchResult
import com.example.recipe_pocket.ui.recipe.write.CookWrite01Activity
import com.example.recipe_pocket.ui.user.UserPageActivity
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
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

    // 정렬 옵션
    private enum class SortOrder { LATEST, VIEWS, LIKES, BOOKMARKS }
    private var currentSortOrder = SortOrder.LATEST

    // 현재 선택된 카테고리
    private var selectedCategory: String = "전체"

    // 캐시된 레시피 목록
    private var recipeListCache = listOf<Recipe>()

    // 현재 필터 상태
    private var currentFilter = SearchFilter.default()

    // 카테고리 태그 뷰들
    private val categoryTags = mutableMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CategoryPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //setupWindowInsets()
        val initialCategory = intent.getStringExtra(EXTRA_INITIAL_CATEGORY)
        setupBackButton()
        setupRecyclerView()
        setupCategoryTags()
        setupFilterAndSortButtons()
        setupBottomNavigation()

        val resolvedCategory = initialCategory?.takeIf { categoryTags.containsKey(it) } ?: "전체"
        selectedCategory = resolvedCategory
        updateCategoryUI(resolvedCategory)
        loadRecipesByCategory(resolvedCategory)
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigationView.menu.findItem(R.id.fragment_home).isChecked = true
    }


    // FilterBottomSheetFragment.OnFilterAppliedListener 인터페이스 구현
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
        categoryTags["중식"] = binding.tagChinese
        categoryTags["일식"] = binding.tagJapanese
        categoryTags["양식"] = binding.tagWestern
        categoryTags["분식"] = binding.tagSnack
        categoryTags["디저트"] = binding.tagDessert
        categoryTags["음료"] = binding.tagDrink
        categoryTags["기타"] = binding.tagOther

        // 각 카테고리 태그에 클릭 리스너 설정
        categoryTags.forEach { (category, textView) ->
            textView.setOnClickListener {
                selectCategory(category)
            }
        }

        // 초기 선택 상태 설정 (전체)
    }

    private fun selectCategory(category: String) {
        if (selectedCategory != category) {
            selectedCategory = category
            updateCategoryUI(category)
            loadRecipesByCategory(category)
        }
    }

    private fun updateCategoryUI(selectedCategory: String) {
        categoryTags.forEach { (category, textView) ->
            if (category == selectedCategory) {
                // 선택된 카테고리 스타일
                textView.setBackgroundResource(R.drawable.bg_category_tag_selected)
                textView.setTextColor(ContextCompat.getColor(this, R.color.white))
            } else {
                // 선택되지 않은 카테고리 스타일
                textView.setBackgroundResource(R.drawable.bg_category_tag_normal)
                textView.setTextColor(ContextCompat.getColor(this, R.color.black))
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
        // 1. 필터링 적용
        val filteredList = filterRecipes(recipeListCache, currentFilter)

        // 2. 정렬 적용
        val sortedList = when (currentSortOrder) {
            SortOrder.LATEST -> filteredList.sortedByDescending { it.createdAt }
            SortOrder.VIEWS -> filteredList.sortedByDescending { it.viewCount ?: 0 }
            SortOrder.LIKES -> filteredList.sortedByDescending { it.likeCount ?: 0 }
            SortOrder.BOOKMARKS -> filteredList.sortedByDescending { it.bookmarkedBy?.size ?: 0 }
        }

        // 3. UI 업데이트
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

            // 카테고리 필터는 이미 loadRecipesByCategory에서 처리했으므로 여기서는 제외
            // SearchFilter가 지원하는 다른 필터들이 있다면 여기에 추가

            creationDateCheck
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.fragment_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.fragment_search -> {
                    startActivity(Intent(this, SearchResult::class.java))
                    finish()
                    true
                }
                R.id.fragment_favorite -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        startActivity(Intent(this, CookWrite01Activity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    true
                }
                R.id.fragment_another -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        startActivity(Intent(this, BookmarkActivity::class.java))
                    } else {
                        Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
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
}