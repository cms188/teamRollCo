package com.example.recipe_pocket.ui.category

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import com.example.recipe_pocket.CategoryPageActivity
import com.example.recipe_pocket.R
import com.example.recipe_pocket.databinding.ActivityAllCategoriesBinding
import utils.ToolbarUtils

class AllCategoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllCategoriesBinding
    private lateinit var btnback: ImageButton

    private val categoryItems = listOf(
        CategoryItem("한식", R.drawable.cate1_01, "한식"),
        CategoryItem("양식", R.drawable.cate1_02, "양식"),
        CategoryItem("중식", R.drawable.cate1_03, "중식"),
        CategoryItem("일식", R.drawable.cate1_04, "일식"),
        CategoryItem("디저트", R.drawable.cate1_05, "디저트"),
        CategoryItem("분식", R.drawable.cate1_06, "분식"),
        CategoryItem("음료", R.drawable.cate1_07, "음료"),
        CategoryItem("기타", R.drawable.cate1_08, "기타")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ToolbarUtils.setupTransparentToolbar(this, "비밀번호 변경")

        setupWindowInsets()
        setupCategoryGrid()

        btnback = findViewById(R.id.back_button)
        btnback.setOnClickListener {
            finish()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupCategoryGrid() {
        binding.recyclerViewCategories.apply {
            layoutManager = GridLayoutManager(this@AllCategoriesActivity, 4)
            adapter = CategoryGridAdapter(categoryItems) { item ->
                startActivity(CategoryPageActivity.createIntent(this@AllCategoriesActivity, item.categoryKey))
            }
        }
    }
}
