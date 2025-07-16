package com.example.recipe_pocket.ui.recipe.search

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.ui.user.UserPageActivity
import com.example.recipe_pocket.databinding.SearchResultBinding
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.recipe.write.CookWrite01Activity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SearchResult : AppCompatActivity() {

    private lateinit var binding: SearchResultBinding
    private lateinit var recipeAdapter: RecipeAdapter
    private var currentQuery: String? = null // 현재 검색어를 저장하는 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupBackButton()
        setupRecyclerView()
        setupSearch()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadData(currentQuery)
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
            layoutManager = LinearLayoutManager(this@SearchResult, RecyclerView.VERTICAL, false)
        }
    }

    private fun setupSearch() {
        binding.etSearchBar.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = textView.text.toString().trim()
                currentQuery = query
                loadData(currentQuery)
                hideKeyboard(textView)
                true
            } else {
                false
            }
        }
    }

    private fun loadData(query: String?) {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewSearchResults.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE

        lifecycleScope.launch {
            val result = if (query.isNullOrBlank()) {
                RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 10)
            } else {
                RecipeLoader.searchRecipesByTitle(query)
            }

            binding.progressBar.visibility = View.GONE
            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        val message = if (query.isNullOrBlank()) "표시할 레시피가 없습니다." else "'${query}'에 대한 검색 결과가 없습니다."
                        binding.tvNoResults.text = message
                        binding.tvNoResults.visibility = View.VISIBLE
                        recipeAdapter.updateRecipes(emptyList())
                    } else {
                        binding.recyclerViewSearchResults.visibility = View.VISIBLE
                        binding.tvNoResults.visibility = View.GONE
                        recipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = {
                    binding.tvNoResults.text = "데이터를 불러오는 중 오류가 발생했습니다."
                    binding.tvNoResults.visibility = View.VISIBLE
                }
            )
        }
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
        view.clearFocus()
    }
}