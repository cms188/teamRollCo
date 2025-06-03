package com.example.recipe_pocket

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        // Edge-to-edge 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.SearchResultLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                //topMargin = insets.top
            }
            WindowInsetsCompat.CONSUMED
        }
        setupBackButton()
        setupRecyclerView()
        setupSearch()

        // 화면이 열릴 때 초기 (예: 인기 또는 무작위) 레시피 로드
        loadInitialRecipes()
    }

    private fun setupBackButton() {
        binding.ivBackButton.setOnClickListener { // 바인딩 사용
            finish()
        }
    }

    private fun setupRecyclerView() {
        // SearchResult에서는 cook_card_03.xml을 사용한다고 가정
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewSearchResults.apply { // XML에 정의된 RecyclerView ID 사용
            adapter = recipeAdapter
            // 레이아웃 매니저 설정 (세로 리스트)
            layoutManager = LinearLayoutManager(this@SearchResult, RecyclerView.VERTICAL, false)
        }
    }

    private fun setupSearch() {
        binding.etSearchBar.setOnEditorActionListener { textView, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val searchQuery = textView.text.toString().trim()
                performSearch(searchQuery)
                hideKeyboard(textView)
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            Toast.makeText(this, "검색어를 입력해주세요.", Toast.LENGTH_SHORT).show()
            // 선택적으로 초기 레시피를 다시 로드하거나 결과를 지웁니다.
            // loadInitialRecipes()
            // recipeAdapter.updateRecipes(emptyList())
            // binding.tvNoResults.visibility = View.GONE // 결과 없음 텍스트뷰가 있다면
            return
        }

        binding.progressBar.visibility = View.VISIBLE // ProgressBar 표시
        binding.recyclerViewSearchResults.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE // 결과 없음 텍스트뷰가 있다면 숨김

        lifecycleScope.launch {
            val result = RecipeLoader.searchRecipesByTitle(query)
            binding.progressBar.visibility = View.GONE // ProgressBar 숨김

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        Toast.makeText(this@SearchResult, "'${query}'에 대한 검색 결과가 없습니다.", Toast.LENGTH_LONG).show()
                        binding.recyclerViewSearchResults.visibility = View.GONE
                        binding.tvNoResults.text = "'${query}'에 대한\n검색 결과가 없습니다."
                        binding.tvNoResults.visibility = View.VISIBLE
                        recipeAdapter.updateRecipes(emptyList()) // 이전 결과 지우기
                    } else {
                        binding.recyclerViewSearchResults.visibility = View.VISIBLE
                        binding.tvNoResults.visibility = View.GONE
                        recipeAdapter.updateRecipes(recipes)
                        binding.recyclerViewSearchResults.smoothScrollToPosition(0) // 맨 위로 스크롤
                    }
                },
                onFailure = { exception ->
                    Toast.makeText(this@SearchResult, "검색 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.tvNoResults.text = "검색 중 오류가 발생했습니다."
                    binding.tvNoResults.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun loadInitialRecipes() { // 함수 이름 변경됨
        binding.progressBar.visibility = View.VISIBLE // ProgressBar 표시
        binding.recyclerViewSearchResults.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE

        lifecycleScope.launch {
            // 초기에는 10개의 무작위 레시피를 로드 (더 나은 초기 화면을 위해 늘림)
            val result = RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 10)
            binding.progressBar.visibility = View.GONE // ProgressBar 숨김

            result.fold(
                onSuccess = { recipes ->
                    if (recipes.isEmpty()) {
                        Toast.makeText(this@SearchResult, "표시할 레시피가 없습니다.", Toast.LENGTH_LONG).show()
                        binding.recyclerViewSearchResults.visibility = View.GONE
                        binding.tvNoResults.text = "표시할 레시피가 없습니다."
                        binding.tvNoResults.visibility = View.VISIBLE
                    } else {
                        binding.recyclerViewSearchResults.visibility = View.VISIBLE
                        binding.tvNoResults.visibility = View.GONE
                        recipeAdapter.updateRecipes(recipes)
                    }
                },
                onFailure = { exception ->
                    Toast.makeText(this@SearchResult, "초기 레시피 로드 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.tvNoResults.text = "레시피 로드에 실패했습니다."
                    binding.tvNoResults.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }
}