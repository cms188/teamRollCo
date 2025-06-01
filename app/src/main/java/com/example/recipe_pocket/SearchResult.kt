package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager // RecyclerView용 LayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.MainActivity
// Glide는 어댑터에서 사용
import com.example.recipe_pocket.databinding.SearchResultBinding
import kotlinx.coroutines.launch

class SearchResult : AppCompatActivity() {

    private lateinit var binding: SearchResultBinding
    private lateinit var recipeAdapter: RecipeAdapter // 어댑터 선언

    // 기본 이미지 관련 상수는 어댑터로 이동했거나, 필요시 여기서도 정의 가능

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SearchResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.SearchResultLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> { // 루트 레이아웃 타입에 맞게 수정
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        val backButton: ImageView = findViewById(R.id.iv_back_button)
        backButton.setOnClickListener {
            finish()
        }

        // Hot Cook RecyclerView 설정
        setupRecyclerView() // RecyclerView 설정 함수 호출
        loadRecipes() // 함수 이름 변경 또는 내용 수정
    }
    private fun setupRecyclerView() {
        // MainActivity에서는 cook_card_01.xml을 사용한다고 가정
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewSearchResults.apply { // XML에 정의한 RecyclerView ID 사용
            adapter = recipeAdapter
            // 레이아웃 매니저 설정 (세로 리스트)
            layoutManager = LinearLayoutManager(this@SearchResult, RecyclerView.VERTICAL, false)
            // 만약 가로 리스트를 원한다면:
            // layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
            // 아이템 간격 등이 필요하면 ItemDecoration 추가 가능
        }
    }

    // 카드에 db내용 추가 (함수 이름 변경 또는 내용 조정)
    private fun loadRecipes() {
        lifecycleScope.launch {
            // RecipeLoader에서 레시피를 요청합니다.
            // RecyclerView를 사용하므로 3개 이상도 가져올 수 있습니다.
            // 여기서는 예시로 5개를 가져오도록 변경 (필요에 따라 조절)
            val result = RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5)

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        Toast.makeText(this@SearchResult, "표시할 인기 레시피가 없습니다.", Toast.LENGTH_LONG).show()
                        binding.recyclerViewSearchResults.visibility = RecyclerView.GONE // 데이터 없으면 숨김
                        // 필요하다면 "데이터 없음"을 표시하는 TextView를 보이게 할 수 있음
                    } else {
                        binding.recyclerViewSearchResults.visibility = RecyclerView.VISIBLE // 데이터 있으면 보임
                        recipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = { exception ->
                    Toast.makeText(this@SearchResult, "인기 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    binding.recyclerViewSearchResults.visibility = RecyclerView.GONE // 실패 시에도 숨김
                    // 필요하다면 에러 메시지를 표시하는 TextView를 보이게 할 수 있음
                }
            )
        }
    }
}