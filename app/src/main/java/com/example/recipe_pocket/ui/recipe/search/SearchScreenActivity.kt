package com.example.recipe_pocket.ui.recipe.search

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.example.recipe_pocket.R
import com.example.recipe_pocket.databinding.SearchScreenBinding
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.ui.recipe.write.CookWrite01Activity
import com.example.recipe_pocket.ui.user.UserPageActivity
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SearchScreenActivity : AppCompatActivity() {

    private lateinit var binding: SearchScreenBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var firestore: FirebaseFirestore
    private val gson = Gson()

    // 최근 검색어 저장을 위한 키
    private val PREF_KEY_RECENT_SEARCHES = "recent_searches"
    private val MAX_RECENT_SEARCHES = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SearchScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("search_prefs", MODE_PRIVATE)
        firestore = FirebaseFirestore.getInstance()

        setupWindowInsets()
        setupViews()
        loadRecentSearches()
        loadPopularSearches()
        loadRecommendedSearches()
        setupBottomNavigation()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchScreenLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupViews() {
        // 뒤로가기 버튼
        binding.ivBackButton.setOnClickListener {
            finish()
        }

        // 검색바 엔터 처리
        binding.etSearchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchBar.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(query: String) {
        // 최근 검색어에 추가
        saveRecentSearch(query)

        // 검색 통계 업데이트 (Firebase)
        updateSearchStatistics(query)

        // SearchResult 액티비티로 이동
        val intent = Intent(this, SearchResult::class.java).apply {
            putExtra("search_query", query)
        }
        startActivity(intent)
    }

    private fun saveRecentSearch(query: String) {
        val recentSearches = getRecentSearches().toMutableList()

        // 이미 있는 검색어면 제거
        recentSearches.remove(query)

        // 맨 앞에 추가
        recentSearches.add(0, query)

        // 최대 개수 제한
        if (recentSearches.size > MAX_RECENT_SEARCHES) {
            recentSearches.removeAt(recentSearches.size - 1)
        }

        // 저장
        val json = gson.toJson(recentSearches)
        sharedPreferences.edit().putString(PREF_KEY_RECENT_SEARCHES, json).apply()

        // UI 업데이트
        loadRecentSearches()
    }

    private fun getRecentSearches(): List<String> {
        val json = sharedPreferences.getString(PREF_KEY_RECENT_SEARCHES, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun loadRecentSearches() {
        val recentSearches = getRecentSearches()
        binding.recentSearchContainer.removeAllViews()

        recentSearches.forEach { searchTerm ->
            val itemView = createRecentSearchItem(searchTerm)
            binding.recentSearchContainer.addView(itemView)
        }
    }

    private fun createRecentSearchItem(searchTerm: String): View {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_recent_search, null)
        val container = itemView.findViewById<LinearLayout>(R.id.search_item_container)
        val textView = itemView.findViewById<TextView>(R.id.search_text)
        val deleteButton = itemView.findViewById<ImageView>(R.id.delete_button)

        textView.text = searchTerm

        // 검색어 클릭
        container.setOnClickListener {
            performSearch(searchTerm)
        }

        // 삭제 버튼 클릭
        deleteButton.setOnClickListener {
            removeRecentSearch(searchTerm)
        }

        return itemView
    }

    private fun removeRecentSearch(searchTerm: String) {
        val recentSearches = getRecentSearches().toMutableList()
        recentSearches.remove(searchTerm)

        val json = gson.toJson(recentSearches)
        sharedPreferences.edit().putString(PREF_KEY_RECENT_SEARCHES, json).apply()

        loadRecentSearches()
    }

    private fun updateSearchStatistics(query: String) {
        lifecycleScope.launch {
            try {
                val searchDoc = firestore.collection("search_statistics")
                    .document(query.lowercase())

                searchDoc.update("count", FieldValue.increment(1))
                    .addOnFailureListener {
                        // 문서가 없으면 새로 생성
                        searchDoc.set(mapOf(
                            "keyword" to query,
                            "count" to 1,
                            "lastSearched" to FieldValue.serverTimestamp()
                        ))
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadPopularSearches() {
        lifecycleScope.launch {
            try {
                val popularSearches = firestore.collection("search_statistics")
                    .orderBy("count", Query.Direction.DESCENDING)
                    .limit(6)
                    .get()
                    .await()

                val keywords = popularSearches.documents.mapNotNull {
                    it.getString("keyword")
                }

                // UI 업데이트
                updatePopularSearchUI(keywords)

            } catch (e: Exception) {
                e.printStackTrace()
                // 기본값 표시
                updatePopularSearchUI(listOf("김치찌개", "된장찌개", "파스타", "샐러드", "볶음밥", "카레"))
            }
        }
    }

    private fun updatePopularSearchUI(keywords: List<String>) {
        val popularViews = listOf(
            binding.popular1, binding.popular2, binding.popular3,
            binding.popular4, binding.popular5, binding.popular6
        )

        keywords.forEachIndexed { index, keyword ->
            if (index < popularViews.size) {
                popularViews[index].apply {
                    text = keyword
                    setOnClickListener { performSearch(keyword) }
                }
            }
        }
    }

    private fun loadRecommendedSearches() {
        // 계절별 추천 검색어 (실제로는 Firebase에서 가져올 수 있음)
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        val seasonalKeywords = when (currentMonth) {
            11, 0, 1 -> listOf("김치찌개", "부대찌개", "어묵탕", "호빵", "붕어빵", "호떡")
            2, 3, 4 -> listOf("봄나물", "딸기", "샐러드", "비빔밥", "나물", "쌈밥")
            5, 6, 7 -> listOf("냉면", "콩국수", "빙수", "화채", "냉채", "오이냉국")
            else -> listOf("전", "송편", "잡채", "갈비찜", "식혜", "수정과")
        }

        updateRecommendedSearchUI(seasonalKeywords)
    }

    private fun updateRecommendedSearchUI(keywords: List<String>) {
        val recommendedViews = listOf(
            binding.recommended1, binding.recommended2, binding.recommended3,
            binding.recommended4, binding.recommended5, binding.recommended6
        )

        keywords.forEachIndexed { index, keyword ->
            if (index < recommendedViews.size) {
                recommendedViews[index].apply {
                    text = keyword
                    setOnClickListener { performSearch(keyword) }
                }
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigationView.selectedItemId = R.id.fragment_search

        binding.bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.fragment_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.fragment_search -> true
                R.id.fragment_another -> {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        startActivity(Intent(this, CookWrite01Activity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    true
                }
                R.id.fragment_favorite -> {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        startActivity(Intent(this, BookmarkActivity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    true
                }
                R.id.fragment_settings -> {
                    if (FirebaseAuth.getInstance().currentUser != null) {
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
}