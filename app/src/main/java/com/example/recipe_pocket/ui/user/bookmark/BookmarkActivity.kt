package com.example.recipe_pocket.ui.user.bookmark

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.databinding.ActivityBookmarkBinding
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.recipe.search.SearchResult
import com.example.recipe_pocket.ui.main.WriteChoiceDialogFragment
import com.example.recipe_pocket.ui.user.UserPageActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class BookmarkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarkBinding
    private lateinit var recipeAdapter: RecipeAdapter

    private val bottomNavigationView: BottomNavigationView
        get() = binding.bottomNavigationView.bottomNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // UI 관련 설정은 onCreate에서 한 번만 수행
        utils.ToolbarUtils.setupTransparentToolbar(this, "")
        setupRecyclerView()
        setupBottomNavigation()
        utils.ToolbarUtils.setupTransparentToolbar(this, "북마크")
    }

    override fun onResume() {
        super.onResume()
        // 데이터 로딩과 UI 상태 업데이트는 onResume에서 수행
        loadBookmarkedRecipes()
        bottomNavigationView.menu.findItem(R.id.fragment_another).isChecked = true
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewBookmarks.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@BookmarkActivity)
        }
    }

    private fun loadBookmarkedRecipes() {
        // 로그인 상태 확인
        if (Firebase.auth.currentUser == null) {
            binding.tvNoBookmarks.text = "로그인이 필요한 기능입니다."
            binding.tvNoBookmarks.visibility = View.VISIBLE
            binding.recyclerViewBookmarks.visibility = View.GONE
            binding.progressBar.visibility = View.GONE
            return
        }

        // 로딩 시작 UI 처리
        binding.progressBar.visibility = View.VISIBLE
        binding.tvNoBookmarks.visibility = View.GONE
        binding.recyclerViewBookmarks.visibility = View.GONE

        // 코루틴으로 비동기 데이터 로딩
        lifecycleScope.launch {
            val result = RecipeLoader.loadBookmarkedRecipes()
            binding.progressBar.visibility = View.GONE // 로딩 완료 후 ProgressBar 숨김

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        binding.tvNoBookmarks.text = "북마크한 레시피가 없습니다."
                        binding.tvNoBookmarks.visibility = View.VISIBLE
                    } else {
                        binding.recyclerViewBookmarks.visibility = View.VISIBLE
                        binding.tvNoBookmarks.visibility = View.GONE
                        recipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = { exception ->
                    binding.tvNoBookmarks.text = "북마크를 불러오는 중 오류가 발생했습니다."
                    binding.tvNoBookmarks.visibility = View.VISIBLE
                }
            )
        }
    }
    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemReselectedListener { /* no-op */ }

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.fragment_another) {
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
