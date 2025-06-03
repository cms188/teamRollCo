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

        setupWindowInsets()
        setupBackButton()
        setupRecyclerView()
        setupSearch()

        // 화면이 열릴 때 초기 (예: 인기 또는 무작위) 레시피 로드
        loadInitialRecipes()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.SearchResultLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            // 액션 바/툴바에서 처리하지 않는 경우에만 상단 inset을 소비합니다.
            // WindowInsetsCompat.CONSUMED
            // 현재 XML의 경우, 상단 바 레이아웃이 상태 표시줄 아래에 있도록 의도된 것이라면 괜찮습니다.
            // 상태 표시줄에 대한 상단 패딩은 XML의 첫 번째 LinearLayout에서 처리됩니다.
            WindowInsetsCompat.CONSUMED // 상단 패딩이 처리되었다고 가정
        }
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
                hideKeyboard(textView) // 검색 후 키보드 숨기기
                true // 이벤트 소비됨
            } else {
                false // 이벤트 소비 안 됨
            }
        }
        // EditText의 검색 아이콘이 사용자 정의 드로어블인 경우 클릭 가능하게 만들 수도 있습니다.
        // 표준 drawableEnd의 경우 IME_ACTION_SEARCH가 주요 방법입니다.
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
        view.clearFocus() // 선택 사항: EditText에서 포커스 제거
    }
}