package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.ActivityMyRecipesBinding // 바인딩 클래스 이름 변경
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class MyRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyRecipesBinding // 바인딩 클래스 이름 변경
    private lateinit var recipeAdapter: RecipeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRecipesBinding.inflate(layoutInflater) // 바인딩 클래스 이름 변경
        setContentView(binding.root)

        setupRecyclerView()
        setupBackButton()
        loadMyRecipes()
        setupBottomNavigation()
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewBookmarks.apply { // ID는 activity_bookmark.xml과 동일
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@MyRecipesActivity)
        }
    }

    private fun setupBackButton() {
        binding.ivBackButton.setOnClickListener {
            finish()
        }
    }

    private fun loadMyRecipes() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            binding.tvNoBookmarks.text = "로그인이 필요한 기능입니다."
            binding.tvNoBookmarks.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewBookmarks.visibility = View.GONE

        lifecycleScope.launch {
            // RecipeLoader에 추가할 함수 호출
            val result = RecipeLoader.loadRecipesByUserId(currentUser.uid)
            binding.progressBar.visibility = View.GONE

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        binding.tvNoBookmarks.text = "작성한 레시피가 없습니다."
                        binding.tvNoBookmarks.visibility = View.VISIBLE
                    } else {
                        binding.recyclerViewBookmarks.visibility = View.VISIBLE
                        binding.tvNoBookmarks.visibility = View.GONE
                        recipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = {
                    binding.tvNoBookmarks.text = "레시피를 불러오는 중 오류 발생"
                    binding.tvNoBookmarks.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun setupBottomNavigation() {
        // activity_my_recipes.xml의 BottomNavigationView ID가 bottom_navigation_view인지 확인
        binding.bottomNavigationView.setOnItemReselectedListener { /* 아무것도 하지 않음 */ }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            val intent = when (item.itemId) {
                R.id.fragment_home -> Intent(this, MainActivity::class.java)
                R.id.fragment_search -> Intent(this, SearchResult::class.java)
                R.id.fragment_another -> Intent(this, BookmarkActivity::class.java)
                R.id.fragment_settings -> Intent(this, UserPageActivity::class.java)
                R.id.fragment_favorite -> {
                    if (Firebase.auth.currentUser != null) {
                        startActivity(Intent(this, CookWrite01Activity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    return@setOnItemSelectedListener true
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