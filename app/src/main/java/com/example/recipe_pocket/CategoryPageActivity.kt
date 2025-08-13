package com.example.recipe_pocket

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.CategoryPageBinding

class CategoryPageActivity : AppCompatActivity() {

    // 뷰 바인딩을 위한 변수 선언
    private lateinit var binding: CategoryPageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 뷰 바인딩을 사용하여 category_page.xml 레이아웃과 연결
        binding = CategoryPageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // XML에 있는 뷰에 접근 (뒤로가기 버튼 클릭 리스너 설정)
        binding.btnBack.setOnClickListener {
            finish()
        }

        // XML에 있는 RecyclerView 설정
        binding.recyclerViewSearchResults.layoutManager = LinearLayoutManager(this)

        // TODO: 여기에 카테고리에 맞는 레시피 목록을 불러와 어댑터에 연결하는 로직을 작성해야 합니다.
        // val adapter = MyRecipeAdapter(recipeList)
        // binding.recyclerViewSearchResults.adapter = adapter
    }
}