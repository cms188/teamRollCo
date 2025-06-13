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
        loadRecipes_hot() // 함수 이름 변경 또는 내용 수정
        loadRecipes_pick()
        loadRecipes_n()

        // ViewPager2 설정 (CookTipItem, CookTipAdapter 클래스가 정의되어 있다고 가정)
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
    private fun setupRecyclerView() {
        // MainActivity에서는 cook_card_01.xml을 사용한다고 가정
        hotCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_01)
        binding.hotCookRecyclerview.apply { // XML에 정의한 RecyclerView ID 사용
            adapter = hotCookRecipeAdapter
            // 레이아웃 매니저 설정 (세로 리스트)
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        pickCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_02)
        binding.pickCookRecyclerview.apply { // XML에 정의한 RecyclerView ID 사용
            adapter = pickCookRecipeAdapter
            // 레이아웃 매니저 설정 (세로 리스트)
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        nCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_02)
        binding.nCookRecyclerview.apply { // XML에 정의한 RecyclerView ID 사용
            adapter = nCookRecipeAdapter
            // 레이아웃 매니저 설정 (세로 리스트)
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }
    }

    // 카드에 db내용 추가 (함수 이름 변경 또는 내용 조정)
    private fun loadRecipes_hot() {
        lifecycleScope.launch {
            val result = RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5)

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        Toast.makeText(this@MainActivity, "표시할 인기 레시피가 없습니다.", Toast.LENGTH_LONG).show()
                        binding.hotCookRecyclerview.visibility = RecyclerView.GONE // 데이터 없으면 숨김
                        // 필요하다면 "데이터 없음"을 표시하는 TextView를 보이게 할 수 있음
                    } else {
                        binding.hotCookRecyclerview.visibility = RecyclerView.VISIBLE // 데이터 있으면 보임
                        hotCookRecipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = { exception ->
                    Toast.makeText(this@MainActivity, "인기 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    binding.hotCookRecyclerview.visibility = RecyclerView.GONE // 실패 시에도 숨김
                    // 필요하다면 에러 메시지를 표시하는 TextView를 보이게 할 수 있음
                }
            )
        }
    }
    private fun loadRecipes_pick() {
        lifecycleScope.launch {
            val result = RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5)

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        Toast.makeText(this@MainActivity, "표시할 인기 레시피가 없습니다.", Toast.LENGTH_LONG).show()
                        binding.pickCookRecyclerview.visibility = RecyclerView.GONE // 데이터 없으면 숨김
                        // 필요하다면 "데이터 없음"을 표시하는 TextView를 보이게 할 수 있음
                    } else {
                        binding.pickCookRecyclerview.visibility = RecyclerView.VISIBLE // 데이터 있으면 보임
                        pickCookRecipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = { exception ->
                    Toast.makeText(this@MainActivity, "인기 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    binding.pickCookRecyclerview.visibility = RecyclerView.GONE // 실패 시에도 숨김
                    // 필요하다면 에러 메시지를 표시하는 TextView를 보이게 할 수 있음
                }
            )
        }
    }
    private fun loadRecipes_n() {
        lifecycleScope.launch {
            val result = RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5)

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        Toast.makeText(this@MainActivity, "표시할 인기 레시피가 없습니다.", Toast.LENGTH_LONG).show()
                        binding.nCookRecyclerview.visibility = RecyclerView.GONE // 데이터 없으면 숨김
                        // 필요하다면 "데이터 없음"을 표시하는 TextView를 보이게 할 수 있음
                    } else {
                        binding.nCookRecyclerview.visibility = RecyclerView.VISIBLE // 데이터 있으면 보임
                        nCookRecipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = { exception ->
                    Toast.makeText(this@MainActivity, "인기 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    binding.nCookRecyclerview.visibility = RecyclerView.GONE // 실패 시에도 숨김
                    // 필요하다면 에러 메시지를 표시하는 TextView를 보이게 할 수 있음
                }
            )
        }
    }
}