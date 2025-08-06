package com.example.recipe_pocket.ui.user

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.databinding.ActivityLikedRecipesBinding
import com.example.recipe_pocket.repository.RecipeLoader
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class LikedRecipesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLikedRecipesBinding
    private lateinit var recipeAdapter: RecipeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLikedRecipesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBackButton()
    }

    override fun onResume() {
        super.onResume()
        loadLikedRecipes()
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewLikedRecipes.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@LikedRecipesActivity)
        }
    }

    private fun setupBackButton() {
        binding.ivBackButton.setOnClickListener {
            finish()
        }
    }

    private fun loadLikedRecipes() {
        if (Firebase.auth.currentUser == null) {
            binding.tvNoLikedRecipes.text = "로그인이 필요한 기능입니다."
            binding.tvNoLikedRecipes.visibility = View.VISIBLE
            binding.recyclerViewLikedRecipes.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoLikedRecipes.visibility = View.GONE
        binding.recyclerViewLikedRecipes.visibility = View.GONE

        lifecycleScope.launch {
            val result = RecipeLoader.loadLikedRecipes()
            binding.progressBar.visibility = View.GONE

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        binding.tvNoLikedRecipes.text = "좋아요한 레시피가 없습니다."
                        binding.tvNoLikedRecipes.visibility = View.VISIBLE
                    } else {
                        binding.recyclerViewLikedRecipes.visibility = View.VISIBLE
                        binding.tvNoLikedRecipes.visibility = View.GONE
                        recipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = {
                    binding.tvNoLikedRecipes.text = "레시피를 불러오는 중 오류가 발생했습니다."
                    binding.tvNoLikedRecipes.visibility = View.VISIBLE
                }
            )
        }
    }
}