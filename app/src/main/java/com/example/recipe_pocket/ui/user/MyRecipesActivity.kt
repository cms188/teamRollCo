package com.example.recipe_pocket.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.databinding.ActivityMyRecipesBinding
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.recipe.search.SearchResult
import com.example.recipe_pocket.ui.main.WriteChoiceDialogFragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MyRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyRecipesBinding
    private lateinit var recipeAdapter: RecipeAdapter

    private val bottomNavigationView: BottomNavigationView
        get() = binding.bottomNavigationView.bottomNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRecipesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        utils.ToolbarUtils.setupTransparentToolbar(this, "내 레시피")
        setupRecyclerView()
        loadMyRecipes()
        setupBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        bottomNavigationView.menu.findItem(R.id.fragment_settings).isChecked = true
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewBookmarks.apply { // ID는 activity_bookmark.xml과 동일
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@MyRecipesActivity)
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
        bottomNavigationView.setOnItemReselectedListener { /* no-op */ }

        bottomNavigationView.setOnItemSelectedListener { item ->
            val currentUser = Firebase.auth.currentUser

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
}