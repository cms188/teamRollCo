package com.example.recipe_pocket

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope // 코루틴 사용을 위해 필요
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

import kotlinx.coroutines.launch // 코루틴 사용을 위해 필요

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var hotCookRecipeAdapter: RecipeAdapter
    private lateinit var pickCookRecipeAdapter: RecipeAdapter
    private lateinit var nCookRecipeAdapter: RecipeAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainFrameLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        // 검색 버튼 클릭 리스너
        binding.topSearchButton.setOnClickListener {
            val intent = Intent(this, SearchResult::class.java)
            startActivity(intent)
        }
        binding.categoryButton1.setOnClickListener {
            val intent = Intent(this, SearchResult::class.java)
            startActivity(intent)
        }

        // Hot Cook RecyclerView 설정
        setupRecyclerView() // RecyclerView 설정 함수 호출
        loadAllRecipes()

        val cookTipItems = listOf(
            CookTipItem("오늘의 추천 요리팁!", "재료 손질부터 플레이팅까지", R.drawable.bg_no_img_gray),
            CookTipItem("간단한 밑반찬 만들기", "냉장고를 든든하게 채워요", R.drawable.bg_no_img_gray),
            CookTipItem("특별한 날 홈파티 메뉴", "쉽고 근사하게 준비하기", R.drawable.bg_no_img_gray)
        )
        val cookTipAdapter = CookTipAdapter(cookTipItems)
        binding.cookTipsViewPager.adapter = cookTipAdapter
        binding.cookTipsViewPager.offscreenPageLimit = 1

        // BottomNavigationView 설정
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.fragment_home -> {
                    true
                }
                R.id.fragment_search -> {
                    startActivity(Intent(this, SearchResult::class.java))
                    true
                }
                R.id.fragment_favorite -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
                        startActivity(Intent(this, CookWrite01Activity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    true
                }
                R.id.fragment_another -> {
                    Toast.makeText(this, "찜 목록 SSZZZ(준비중)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.fragment_settings -> {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    if (currentUser != null) {
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
    private fun loadAllRecipes() {
        lifecycleScope.launch {
            // Hot Cook 로딩 및 UI 업데이트
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
                onSuccess = { recipes ->
                    if (recipes.isNotEmpty()) {
                        binding.hotCookRecyclerview.visibility = RecyclerView.VISIBLE
                        hotCookRecipeAdapter.updateRecipes(recipes)
                    } else {
                        binding.hotCookRecyclerview.visibility = RecyclerView.GONE
                    }
                },
                onFailure = { exception ->
                    binding.hotCookRecyclerview.visibility = RecyclerView.GONE
                    Toast.makeText(this@MainActivity, "인기 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            )

            // Pick Cook 로딩 및 UI 업데이트
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
                onSuccess = { recipes ->
                    if (recipes.isNotEmpty()) {
                        binding.pickCookRecyclerview.visibility = RecyclerView.VISIBLE
                        pickCookRecipeAdapter.updateRecipes(recipes)
                    } else {
                        binding.pickCookRecyclerview.visibility = RecyclerView.GONE
                    }
                },
                onFailure = { exception ->
                    binding.pickCookRecyclerview.visibility = RecyclerView.GONE
                    Toast.makeText(this@MainActivity, "추천 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            )

            // N Cook 로딩 및 UI 업데이트
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
                onSuccess = { recipes ->
                    if (recipes.isNotEmpty()) {
                        binding.nCookRecyclerview.visibility = RecyclerView.VISIBLE
                        nCookRecipeAdapter.updateRecipes(recipes)
                    } else {
                        binding.nCookRecyclerview.visibility = RecyclerView.GONE
                    }
                },
                onFailure = { exception ->
                    binding.nCookRecyclerview.visibility = RecyclerView.GONE
                    Toast.makeText(this@MainActivity, "신규 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
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
    }
}