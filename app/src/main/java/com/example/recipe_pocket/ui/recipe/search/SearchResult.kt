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
    private var currentQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupBackButton()
        setupRecyclerView()
        setupSearch()
        setupBottomNavigation()

        // 전달받은 검색어가 있으면 자동으로 검색
        val searchQuery = intent.getStringExtra("search_query")
        if (!searchQuery.isNullOrBlank()) {
            binding.etSearchBar.setText(searchQuery)
            currentQuery = searchQuery
            loadData(searchQuery)
        }
    }

    override fun onResume() {
        super.onResume()
        // 검색어가 없을 때만 데이터 로드
        if (currentQuery.isNullOrBlank()) {
            loadData(null)
        }
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

                // 검색 통계 업데이트 (SearchScreenActivity와 동일하게)
                updateSearchStatistics(query)
                true
            } else {
                false
            }
        }
    }

    private fun updateSearchStatistics(query: String) {
        if (query.isBlank()) return

        lifecycleScope.launch {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val searchDoc = firestore.collection("search_statistics")
                    .document(query.lowercase())

                searchDoc.update("count", com.google.firebase.firestore.FieldValue.increment(1))
                    .addOnFailureListener {
                        // 문서가 없으면 새로 생성
                        searchDoc.set(mapOf(
                            "keyword" to query,
                            "count" to 1,
                            "lastSearched" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        ))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
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
                    } else {
                        binding.recyclerViewSearchResults.visibility = View.VISIBLE
                        recipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = { exception ->
                    binding.tvNoResults.text = "레시피를 불러오는 중 오류가 발생했습니다."
                    binding.tvNoResults.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.fragment_search

        binding.bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.fragment_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.fragment_search -> {
                    // 현재 SearchResult에 있으므로 SearchScreen으로 이동
                    startActivity(Intent(this, SearchScreenActivity::class.java))
                    finish()
                    true
                }
                R.id.fragment_another -> {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        startActivity(Intent(this, CookWrite01Activity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    true
                }
                R.id.fragment_favorite -> {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        startActivity(Intent(this, BookmarkActivity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    true
                }
                R.id.fragment_settings -> {
                    if (FirebaseAuth.getInstance().currentUser != null) {
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