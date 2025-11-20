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

    // 분류별 카테고리
    private val categoryByType = listOf(
        CategoryItem("한식", R.drawable.cat1_1, "한식"),
        CategoryItem("양식", R.drawable.cat1_2, "양식"),
        CategoryItem("중식", R.drawable.cat1_3, "중식"),
        CategoryItem("일식", R.drawable.cat1_4, "일식"),
        CategoryItem("동남아식", R.drawable.cat1_5, "동남아식"),
        CategoryItem("남미식", R.drawable.cat1_6, "남미식"),
        CategoryItem("분식", R.drawable.cat1_7, "분식")
    )

    // 종류별 카테고리
    private val categoryByDish = listOf(
        CategoryItem("밑반찬", R.drawable.cat2_1, "밑반찬"),
        CategoryItem("국물요리", R.drawable.cat2_2, "국물요리"),
        CategoryItem("밥", R.drawable.cat2_3, "밥"),
        CategoryItem("면", R.drawable.cat2_4, "면"),
        CategoryItem("일품", R.drawable.cat2_5, "일품"),
        CategoryItem("디저트", R.drawable.cat2_6, "디저트"),
        CategoryItem("음료", R.drawable.cat2_7, "음료"),
        CategoryItem("채소요리", R.drawable.cat2_8, "채소요리"),
        CategoryItem("간편식", R.drawable.cat2_9, "간편식"),
        CategoryItem("키즈", R.drawable.cat2_10, "키즈")
    )

    // 상황별/테마 카테고리
    private val categoryByTheme = listOf(
        CategoryItem("캠핑", R.drawable.cat3_1, "캠핑"),
        CategoryItem("제철요리", R.drawable.cat3_2, "제철요리"),
        CategoryItem("초스피드", R.drawable.cat3_3, "초스피드"),
        CategoryItem("손님접대", R.drawable.cat3_4, "손님접대"),
        CategoryItem("명절", R.drawable.cat3_5, "명절")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllCategoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ToolbarUtils.setupTransparentToolbar(this, "전체 카테고리")

        setupWindowInsets()
        setupCategoryGrids()

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

    private fun setupCategoryGrids() {
        // 분류별 섹션
        binding.textCategoryTypeTitle.text = "분류별"
        binding.recyclerViewCategoryType.apply {
            layoutManager = GridLayoutManager(this@AllCategoriesActivity, 5)
            adapter = CategoryGridAdapter(categoryByType) { item ->
                startActivity(
                    CategoryPageActivity.createIntent(
                        this@AllCategoriesActivity,
                        item.categoryKey
                    )
                )
            }
        }

        // 종류별 섹션
        binding.textCategoryDishTitle.text = "종류별"
        binding.recyclerViewCategoryDish.apply {
            layoutManager = GridLayoutManager(this@AllCategoriesActivity, 5)
            adapter = CategoryGridAdapter(categoryByDish) { item ->
                startActivity(
                    CategoryPageActivity.createIntent(
                        this@AllCategoriesActivity,
                        item.categoryKey
                    )
                )
            }
        }

        // 상황별/테마 섹션
        binding.textCategoryThemeTitle.text = "상황별/테마"
        binding.recyclerViewCategoryTheme.apply {
            layoutManager = GridLayoutManager(this@AllCategoriesActivity, 5)
            adapter = CategoryGridAdapter(categoryByTheme) { item ->
                startActivity(
                    CategoryPageActivity.createIntent(
                        this@AllCategoriesActivity,
                        item.categoryKey
                    )
                )
            }
        }
    }
}