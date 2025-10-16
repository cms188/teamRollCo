package com.example.recipe_pocket.ui.recipe.search

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.SearchResultBinding
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.main.WriteChoiceDialogFragment
import com.example.recipe_pocket.ui.user.UserPageActivity
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.google.android.material.chip.Chip
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.*

class SearchResult : AppCompatActivity(), FilterBottomSheetFragment.OnFilterAppliedListener {

    private lateinit var binding: SearchResultBinding
    private lateinit var recipeAdapter: RecipeAdapter
    private var currentQuery: String? = null // 현재 검색어를 저장하는 변수

    private enum class SortOrder { LATEST, VIEWS, LIKES, BOOKMARKS }
    private var currentSortOrder = SortOrder.LATEST
    private var recipeListCache = listOf<Recipe>()

    // 현재 필터 상태를 저장할 변수 추가
    private var currentFilter = SearchFilter.default()

    private var isSuggestionViewVisible = false

    private val bottomNavigationView: BottomNavigationView
        get() = binding.bottomNavigationView.bottomNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupBackButton()
        setupRecyclerView()
        setupSearch()
        // 함수 이름 변경
        setupFilterAndSortButtons()
        setupBottomNavigation()
        handleOnBackPressed()

        // 인텐트에서 검색어 확인 및 처리 로직
        val incomingQuery = intent.getStringExtra("search_query")
        if (!incomingQuery.isNullOrBlank()) {
            // 인텐트로 검색어가 전달된 경우 (예: 제철 재료 클릭)
            binding.etSearchBar.setText(incomingQuery)
            performSearch() // 검색 수행 (UI 숨김, 데이터 로딩 등 포함)
        } else {
            // 직접 검색 화면으로 들어온 경우, 전체 레시피 목록을 불러옴
            loadData(null)
        }
    }

    override fun onResume() {
        super.onResume()
        bottomNavigationView.menu.findItem(R.id.fragment_search).isChecked = true
    }

    // OnFilterAppliedListener 인터페이스의 메소드 구현
    override fun onFilterApplied(filter: SearchFilter) {
        currentFilter = filter
        updateFilterButtonUI()
        applyFiltersAndSort()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.SearchResultLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewSearchResults.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@SearchResult)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSearch() {
        binding.etSearchBar.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.etSearchBar.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showSuggestionView()
            }
        }

        binding.etSearchBar.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // 검색 아이콘 클릭 감지
                val drawableEnd = binding.etSearchBar.compoundDrawables[2]
                if (drawableEnd != null && event.rawX >= (binding.etSearchBar.right - drawableEnd.bounds.width() - binding.etSearchBar.paddingEnd)) {
                    performSearch()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun showSuggestionView() {
        if (isSuggestionViewVisible) return
        isSuggestionViewVisible = true
        binding.suggestionLayout.visibility = View.VISIBLE
        binding.searchResultContainer.visibility = View.GONE
        binding.filterSortLayout.visibility = View.GONE

        loadRecentSearches()
        loadRecommendedSearches()
        loadPopularSearches() // 인기 검색어 로드 함수 호출
    }

    private fun hideSuggestionView() {
        if (!isSuggestionViewVisible) return
        isSuggestionViewVisible = false
        binding.suggestionLayout.visibility = View.GONE
        binding.searchResultContainer.visibility = View.VISIBLE
        binding.filterSortLayout.visibility = View.VISIBLE
        binding.etSearchBar.clearFocus()
        hideKeyboard(binding.etSearchBar)
    }

    private fun loadRecentSearches() {
        binding.chipgroupRecent.removeAllViews()
        val history = SearchHistoryManager.getSearchHistory(this)
        history.forEach { term ->
            val chip = Chip(this).apply {
                text = term
                setChipBackgroundColorResource(R.color.search_color)
                setTextColor(ContextCompat.getColor(this@SearchResult, R.color.black))
                chipCornerRadius = 40f
                isCloseIconVisible = true
                closeIconTint = ContextCompat.getColorStateList(this@SearchResult, R.color.darker_gray)
                setOnCloseIconClickListener {
                    SearchHistoryManager.removeSearchTerm(this@SearchResult, term)
                    loadRecentSearches()
                }
                setOnClickListener {
                    binding.etSearchBar.setText(term)
                    performSearch()
                }
            }
            binding.chipgroupRecent.addView(chip)
        }
    }

    private fun loadRecommendedSearches() {
        binding.chipgroupRecommended.removeAllViews()
        val recommendations = listOf("김치찌개", "제육볶음", "된장찌개", "계란찜", "파스타", "샐러드").shuffled().take(5)
        recommendations.forEach { term ->
            val chip = Chip(this).apply {
                text = term
                setChipBackgroundColorResource(R.color.search_color)
                setTextColor(ContextCompat.getColor(this@SearchResult, R.color.black))
                chipCornerRadius = 40f
                setOnClickListener {
                    binding.etSearchBar.setText(term)
                    performSearch()
                }
            }
            binding.chipgroupRecommended.addView(chip)
        }
    }

    // 인기 검색어 로드 함수 추가
    private fun loadPopularSearches() {
        lifecycleScope.launch {
            binding.chipgroupPopular.removeAllViews()
            val result = SearchStatisticsManager.getPopularSearches()
            result.onSuccess { popularTerms ->
                if (popularTerms.isNotEmpty()) {
                    popularTerms.forEach { term ->
                        val chip = Chip(this@SearchResult).apply {
                            text = term
                            setChipBackgroundColorResource(R.color.search_color)
                            setTextColor(ContextCompat.getColor(this@SearchResult, R.color.black))
                            chipCornerRadius = 40f
                            setOnClickListener {
                                binding.etSearchBar.setText(term)
                                performSearch()
                            }
                        }
                        binding.chipgroupPopular.addView(chip)
                    }
                } else {
                    // 인기 검색어가 없을 경우 처리 (예: 메시지 표시 또는 숨김)
                    // 필요하다면 여기에 TextView를 추가하여 "인기 검색어가 없습니다" 메시지를 표시할 수 있습니다.
                }
            }.onFailure {
                // 오류 처리
                it.printStackTrace()
            }
        }
    }

    private fun performSearch() {
        val query = binding.etSearchBar.text.toString().trim()
        if (query.isNotEmpty()) {
            SearchHistoryManager.addSearchTerm(this, query)
            // 검색 시 통계 업데이트
            lifecycleScope.launch {
                SearchStatisticsManager.updateSearchTerm(query)
            }
        }
        currentQuery = query
        hideSuggestionView()
        loadData(currentQuery)
    }

    // 필터/정렬 버튼 설정 함수로 통합
    private fun setupFilterAndSortButtons() {
        binding.tvSortButton.text = "최신순"

        binding.tvFilterButton.setOnClickListener {
            val filterFragment = FilterBottomSheetFragment.newInstance(currentFilter)
            filterFragment.show(supportFragmentManager, filterFragment.tag)
        }

        binding.tvSortButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(this, view)
            popupMenu.menuInflater.inflate(R.menu.sort_options, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.sort_latest -> currentSortOrder = SortOrder.LATEST
                    R.id.sort_views -> currentSortOrder = SortOrder.VIEWS
                    R.id.sort_likes -> currentSortOrder = SortOrder.LIKES
                    R.id.sort_bookmarks -> currentSortOrder = SortOrder.BOOKMARKS
                }
                binding.tvSortButton.text = menuItem.title
                applyFiltersAndSort() // 정렬 후에도 필터+정렬 적용
                true
            }
            popupMenu.show()
        }
    }

    private fun loadData(query: String?) {
        hideSuggestionView()
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewSearchResults.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE

        lifecycleScope.launch {
            val recipeResult = if (query.isNullOrBlank()) {
                RecipeLoader.loadAllRecipes()
            } else {
                RecipeLoader.searchRecipes(query)
            }

            recipeResult.fold(
                onSuccess = { recipes ->
                    // Firestore에서 가져온 원본 리스트를 캐시에 저장
                    recipeListCache = recipes

                    binding.progressBar.visibility = View.GONE
                    // 필터와 정렬을 적용하여 화면에 표시
                    applyFiltersAndSort()
                },
                onFailure = {
                    binding.progressBar.visibility = View.GONE
                    binding.tvNoResults.text = "데이터를 불러오는 중 오류가 발생했습니다."
                    binding.tvNoResults.visibility = View.VISIBLE
                }
            )
        }
    }

    // 필터링과 정렬을 모두 처리하는 함수로 변경
    private fun applyFiltersAndSort() {
        // 1. 필터링 적용
        val filteredList = filterRecipes(recipeListCache, currentFilter)

        // 2. 정렬 적용
        val sortedList = when (currentSortOrder) {
            SortOrder.LATEST -> filteredList.sortedByDescending { it.createdAt?.toDate() }
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
            val message = if (currentQuery.isNullOrBlank()) "조건에 맞는 레시피가 없습니다." else "'${currentQuery}'에 대한 검색 결과가 없습니다."
            binding.tvNoResults.text = message
            binding.tvNoResults.visibility = View.VISIBLE
            binding.recyclerViewSearchResults.visibility = View.GONE
        }
    }

    // 필터링 로직을 수행하는 별도 함수
    private fun filterRecipes(recipes: List<Recipe>, filter: SearchFilter): List<Recipe> {
        if (filter.isDefault()) return recipes // 기본 필터면 필터링 안함

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

    // 필터 버튼 UI 업데이트 함수
    private fun updateFilterButtonUI() {
        if (currentFilter.isDefault()) {
            binding.tvFilterButton.setTextColor(ContextCompat.getColor(this, R.color.darker_gray))
            binding.tvFilterButton.setBackgroundResource(R.drawable.bg_filter_button_normal)
        } else {
            binding.tvFilterButton.setTextColor(ContextCompat.getColor(this, R.color.orange))
            binding.tvFilterButton.setBackgroundResource(R.drawable.bg_filter_button_active)
        }
    }

    private fun handleOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSuggestionViewVisible) {
                    hideSuggestionView()
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemReselectedListener { /* no-op */ }

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.fragment_search) {
                return@setOnItemSelectedListener true
            }

            val currentUser = FirebaseAuth.getInstance().currentUser

            if (item.itemId == R.id.fragment_favorite) {
                if (currentUser != null) {
                    WriteChoiceDialogFragment().show(supportFragmentManager, WriteChoiceDialogFragment.TAG)
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                return@setOnItemSelectedListener true
            }

            val intent = when (item.itemId) {
                R.id.fragment_home -> Intent(this, MainActivity::class.java)
                R.id.fragment_another -> Intent(this, BookmarkActivity::class.java)
                R.id.fragment_settings -> {
                    if (currentUser != null) Intent(this, UserPageActivity::class.java)
                    else Intent(this, LoginActivity::class.java)
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

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}