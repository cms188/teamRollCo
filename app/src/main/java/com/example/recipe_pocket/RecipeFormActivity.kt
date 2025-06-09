package com.example.recipe_pocket

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import com.example.recipe_pocket.R // ⚠️ 자신의 프로젝트 패키지명으로 변경해주세요!
import com.example.recipe_pocket.databinding.CookWrite02Binding // ⚠️ 자신의 패키지명으로 변경해주세요!

class RecipeFormActivity : AppCompatActivity() {

    // 1. View Binding 선언 (cook_write_02.xml -> CookWrite02Binding)
    private lateinit var binding: CookWrite02Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. View Binding 초기화 및 화면 설정
        binding = CookWrite02Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. 앱 시작 시 기본 항목 1개씩 추가
        addIngredientRow()
        addToolRow()

        // 4. '+ 추가' 버튼 리스너 설정
        setupAddButtonListeners()
    }

    private fun setupAddButtonListeners() {
        // 재료 추가(+) 버튼 클릭 이벤트
        binding.buttonAddIngredient.setOnClickListener {
            addIngredientRow()
        }

        // 조리 도구 추가(+) 버튼 클릭 이벤트
        binding.buttonAddTool.setOnClickListener {
            addToolRow()
        }
    }

    /**
     * 재료 입력 행(item_ingredient.xml)을 동적으로 추가하는 함수
     */
    private fun addIngredientRow() {
        val inflater = LayoutInflater.from(this)
        // item_ingredient.xml 레이아웃을 뷰 객체로 생성
        val ingredientView = inflater.inflate(R.layout.item_ingredient, binding.ingredientsContainer, false)

        // 생성된 뷰 내부의 위젯들을 찾음
        val deleteButton = ingredientView.findViewById<ImageButton>(R.id.delete_material)
        val unitSpinner = ingredientView.findViewById<Spinner>(R.id.spinner_unit)

        // Spinner 설정
        // arrays.xml에 정의된 목록을 가져와 어댑터 생성
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.ingredient_units, // res/values/arrays.xml 의 목록
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitSpinner.adapter = adapter // 스피너에 어댑터 연결

        // 삭제 버튼 클릭 이벤트 설정
        deleteButton.setOnClickListener {
            // 부모 뷰(ingredients_container)에서 현재 뷰(ingredientView)를 제거
            binding.ingredientsContainer.removeView(ingredientView)
        }

        // 완성된 재료 뷰를 컨테이너에 추가
        binding.ingredientsContainer.addView(ingredientView)
    }

    /**
     * 조리 도구 입력 행(item_tool.xml)을 동적으로 추가하는 함수
     */
    private fun addToolRow() {
        val inflater = LayoutInflater.from(this)
        // item_tool.xml 레이아웃을 뷰 객체로 생성
        val toolView = inflater.inflate(R.layout.item_tool, binding.toolsContainer, false)

        // 생성된 뷰 내부의 삭제 버튼을 찾음
        val deleteButton = toolView.findViewById<ImageButton>(R.id.button_delete)

        // 삭제 버튼 클릭 이벤트 설정
        deleteButton.setOnClickListener {
            // 부모 뷰(tools_container)에서 현재 뷰(toolView)를 제거
            binding.toolsContainer.removeView(toolView)
        }

        // 완성된 조리 도구 뷰를 컨테이너에 추가
        binding.toolsContainer.addView(toolView)
    }
}