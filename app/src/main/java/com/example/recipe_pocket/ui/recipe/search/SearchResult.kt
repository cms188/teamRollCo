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
import com.example.recipe_pocket.ui.recipe.write.CookWrite01Activity
import com.example.recipe_pocket.ui.user.UserPageActivity
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SearchResult : AppCompatActivity() {

    private lateinit var binding: SearchResultBinding
    private lateinit var recipeAdapter: RecipeAdapter
    private var currentQuery: String? = null // 현재 검색어를 저장하는 변수

    private enum class SortOrder { LATEST, VIEWS, LIKES, BOOKMARKS }
    private var currentSortOrder = SortOrder.LATEST
    private var recipeListCache = listOf<Recipe>()

    private var isSuggestionViewVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupBackButton()
        setupRecyclerView()
        setupSearch()
        setupSortButton()
        setupBottomNavigation()
        handleOnBackPressed()

        // 액티비티가 처음 생성될 때 전체 레시피 목록을 불러옵니다.
        loadData(null)
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigationView.menu.findItem(R.id.fragment_search).isChecked = true
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
        binding.ivBackButton.setOnClickListener {
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

    private fun performSearch() {
        val query = binding.etSearchBar.text.toString().trim()
        if (query.isNotEmpty()) {
            SearchHistoryManager.addSearchTerm(this, query)
        }
        currentQuery = query
        hideSuggestionView()
        loadData(currentQuery)
    }

    private fun setupSortButton() {
        binding.tvSortButton.text = "최신순"
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
                sortAndDisplayRecipes()
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
                RecipeLoader.searchRecipesByTitle(query)
            }

            recipeResult.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        binding.progressBar.visibility = View.GONE
                        val message = if (query.isNullOrBlank()) "표시할 레시피가 없습니다." else "'${query}'에 대한 검색 결과가 없습니다."
                        binding.tvNoResults.text = message
                        binding.tvNoResults.visibility = View.VISIBLE
                        recipeAdapter.updateRecipes(emptyList())
                        return@launch
                    }

                    val userIds = recipes.mapNotNull { it.userId }.distinct()
                    RecipeLoader.loadUsers(userIds).fold(
                        onSuccess = { usersMap ->
                            recipes.forEach { recipe ->
                                recipe.author = usersMap[recipe.userId]
                                FirebaseAuth.getInstance().currentUser?.uid?.let { currentUserId ->
                                    recipe.isBookmarked = recipe.bookmarkedBy?.contains(currentUserId) == true
                                    recipe.isLiked = recipe.likedBy?.contains(currentUserId) == true
                                }
                            }
                            recipeListCache = recipes
                            binding.progressBar.visibility = View.GONE
                            sortAndDisplayRecipes()
                        },
                        onFailure = {
                            binding.progressBar.visibility = View.GONE
                            binding.tvNoResults.text = "작성자 정보를 불러오는 중 오류 발생"
                            binding.tvNoResults.visibility = View.VISIBLE
                        }
                    )
                },
                onFailure = {
                    binding.progressBar.visibility = View.GONE
                    binding.tvNoResults.text = "데이터를 불러오는 중 오류가 발생했습니다."
                    binding.tvNoResults.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun sortAndDisplayRecipes() {
        val sortedList = when (currentSortOrder) {
            SortOrder.LATEST -> recipeListCache.sortedByDescending { it.createdAt?.toDate() }
            SortOrder.VIEWS -> recipeListCache.sortedByDescending { it.viewCount ?: 0 }
            SortOrder.LIKES -> recipeListCache.sortedByDescending { it.likeCount ?: 0 }
            SortOrder.BOOKMARKS -> recipeListCache.sortedByDescending { it.bookmarkedBy?.size ?: 0 }
        }

        if (sortedList.isNotEmpty()) {
            binding.recyclerViewSearchResults.visibility = View.VISIBLE
            binding.tvNoResults.visibility = View.GONE
            recipeAdapter.updateRecipes(sortedList)
        } else {
            binding.tvNoResults.visibility = View.VISIBLE
            binding.recyclerViewSearchResults.visibility = View.GONE
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
        binding.bottomNavigationView.setOnItemReselectedListener { /* 아무것도 하지 않음 */ }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.fragment_search) {
                return@setOnItemSelectedListener true
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            val intent = when (item.itemId) {
                R.id.fragment_home -> Intent(this, MainActivity::class.java)
                R.id.fragment_another -> Intent(this, BookmarkActivity::class.java)
                R.id.fragment_favorite -> {
                    if (currentUser != null) Intent(this, CookWrite01Activity::class.java)
                    else Intent(this, LoginActivity::class.java)
                }
                R.id.fragment_settings -> {
                    if (currentUser != null) Intent(this, UserPageActivity::class.java)
                    else Intent(this, LoginActivity::class.java)
                }
                else -> null
            }

            intent?.let {
                if (item.itemId == R.id.fragment_favorite || item.itemId == R.id.fragment_settings) {
                    startActivity(it)
                } else {
                    it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(it)
                    overridePendingTransition(0, 0)
                }
            }
            true
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}