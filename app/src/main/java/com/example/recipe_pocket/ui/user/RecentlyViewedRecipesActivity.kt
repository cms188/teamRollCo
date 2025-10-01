package com.example.recipe_pocket.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.ActivityRecentBinding
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.recipe.search.SearchResult
import com.example.recipe_pocket.ui.main.WriteChoiceDialogFragment
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class RecentlyViewedRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecentBinding
    private lateinit var recipeAdapter: RecipeAdapter

    private val bottomNavigationView: BottomNavigationView
        get() = binding.bottomNavigationView.bottomNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBackButton()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadRecentlyViewedRecipes()
        bottomNavigationView.menu.findItem(R.id.fragment_settings).isChecked = true
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewRecent.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@RecentlyViewedRecipesActivity)
        }
    }

    private fun setupBackButton() {
        binding.ivBackButton.setOnClickListener {
            finish()
        }
    }

    private fun loadRecentlyViewedRecipes() {
        // 로그인 상태 확인
        if (Firebase.auth.currentUser == null) {
            binding.tvNoRecent.text = "로그인이 필요한 기능입니다."
            binding.tvNoRecent.visibility = View.VISIBLE
            binding.recyclerViewRecent.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            return
        }

        // 로딩 시작 UI 처리
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoRecent.visibility = View.GONE
        binding.recyclerViewRecent.visibility = View.GONE

        // SharedPreferences에서 최근 본 레시피 ID 목록 가져오기
        val recentlyViewedList = RecentlyViewedManager.getRecentlyViewed(this)

        if (recentlyViewedList.isEmpty()) {
            binding.progressBar.visibility = View.GONE
            binding.tvNoRecent.text = "최근 본 레시피가 없습니다.\n레시피를 둘러보세요!"
            binding.tvNoRecent.visibility = View.VISIBLE
            return
        }

        // 코루틴으로 비동기 데이터 로딩
        lifecycleScope.launch {
            val recipes = mutableListOf<Recipe>()
            val currentUser = Firebase.auth.currentUser

            for (recentItem in recentlyViewedList) {
                val result = RecipeLoader.loadSingleRecipeWithAuthor(recentItem.recipeId)
                result.onSuccess { recipe ->
                    recipe?.let {
                        // 북마크 상태 설정
                        if (currentUser != null) {
                            it.isBookmarked = it.bookmarkedBy?.contains(currentUser.uid) == true
                        }
                        recipes.add(it)
                    }
                }
            }

            binding.progressBar.visibility = View.GONE // 로딩 완료 후 ProgressBar 숨김

            if (recipes.isEmpty()) {
                binding.tvNoRecent.text = "최근 본 레시피를 불러올 수 없습니다."
                binding.tvNoRecent.visibility = View.VISIBLE
            } else {
                binding.recyclerViewRecent.visibility = View.VISIBLE
                binding.tvNoRecent.visibility = View.GONE
                recipeAdapter.updateRecipes(recipes)
            }
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemReselectedListener { /* no-op */ }

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.fragment_settings) {
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
                R.id.fragment_search -> Intent(this, SearchResult::class.java)
                R.id.fragment_another -> {
                    if (currentUser != null) Intent(this, BookmarkActivity::class.java)
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
}
