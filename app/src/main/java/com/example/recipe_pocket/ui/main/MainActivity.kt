package com.example.recipe_pocket.ui.main

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.example.recipe_pocket.ui.recipe.write.CookWrite01Activity
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.ui.recipe.search.SearchResult
import com.example.recipe_pocket.ui.user.UserPageActivity
import com.example.recipe_pocket.data.CookTipItem
import com.example.recipe_pocket.databinding.ActivityMainBinding
import com.example.recipe_pocket.weather.WeatherMainActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var hotCookRecipeAdapter: RecipeAdapter
    private lateinit var pickCookRecipeAdapter: RecipeAdapter
    private lateinit var nCookRecipeAdapter: RecipeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupClickListeners()
        setupRecyclerView()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        loadAllRecipes()
        binding.bottomNavigationView.menu.findItem(R.id.fragment_home).isChecked = true
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainFrameLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupClickListeners() {
        binding.topSearchButton.setOnClickListener {
            startActivity(Intent(this, SearchResult::class.java))
        }
        binding.categoryButton1.setOnClickListener {
            startActivity(Intent(this, SearchResult::class.java))
        }
    }

    private fun loadAllRecipes() {
        lifecycleScope.launch {
            // Hot Cook 로딩
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
                onSuccess = { recipes ->
                    binding.hotCookRecyclerview.visibility = if (recipes.isNotEmpty()) View.VISIBLE else View.GONE
                    hotCookRecipeAdapter.updateRecipes(recipes)
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "인기 레시피 로드 실패", Toast.LENGTH_SHORT).show()
                }
            )

            // Pick Cook 로딩
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
                onSuccess = { recipes ->
                    binding.pickCookRecyclerview.visibility = if (recipes.isNotEmpty()) View.VISIBLE else View.GONE
                    pickCookRecipeAdapter.updateRecipes(recipes)
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "추천 레시피 로드 실패", Toast.LENGTH_SHORT).show()
                }
            )

            // N Cook 로딩
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
                onSuccess = { recipes ->
                    binding.nCookRecyclerview.visibility = if (recipes.isNotEmpty()) View.VISIBLE else View.GONE
                    nCookRecipeAdapter.updateRecipes(recipes)
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "신규 레시피 로드 실패", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun setupRecyclerView() {
        hotCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_01)
        binding.hotCookRecyclerview.apply {
            adapter = hotCookRecipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        pickCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_02)
        binding.pickCookRecyclerview.apply {
            adapter = pickCookRecipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        nCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_02)
        binding.nCookRecyclerview.apply {
            adapter = nCookRecipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        // ViewPager2 어댑터 설정
        val cookTipItems = listOf(
            CookTipItem("오늘의 추천 요리팁!", "재료 손질부터 플레이팅까지", R.drawable.bg_no_img_gray),
            CookTipItem("간단한 밑반찬 만들기", "냉장고를 든든하게 채워요", R.drawable.bg_no_img_gray),
            CookTipItem("특별한 날 홈파티 메뉴", "쉽고 근사하게 준비하기", R.drawable.bg_no_img_gray)
        )
        binding.cookTipsViewPager.adapter = CookTipAdapter(cookTipItems)
        binding.cookTipsViewPager.offscreenPageLimit = 1
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemReselectedListener { /* 아무것도 하지 않음 */ }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.fragment_home) {
                return@setOnItemSelectedListener true
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            val intent = when (item.itemId) {
                //R.id.fragment_search -> Intent(this, SearchResult::class.java) //임시변경
                R.id.fragment_search -> Intent(this, WeatherMainActivity::class.java) //weathermainactivity로 이동 임시변경
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
}