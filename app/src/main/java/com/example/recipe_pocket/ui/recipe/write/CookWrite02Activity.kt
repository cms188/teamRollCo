package com.example.recipe_pocket.ui.recipe.write

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.databinding.CookWrite02Binding
import com.example.recipe_pocket.databinding.ItemIngredientBinding
import com.example.recipe_pocket.databinding.ItemToolBinding

class CookWrite02Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite02Binding
    private var recipeData: RecipeData? = null

    // 데이터 목록은 그대로 유지
    private val ingredientsList = mutableListOf<Ingredient>()
    private val toolsList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite02Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        loadInitialData()
        // RecyclerView 설정 대신 동적 뷰 설정 함수 호출
        setupIngredientViews()
        setupToolViews()
        setupToolbarAndListeners()
    }

    private fun loadInitialData() {
        recipeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("recipe_data", RecipeData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("recipe_data") as? RecipeData
        }

        if (recipeData == null) {
            Toast.makeText(this, "데이터 로딩 실패!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 재료 목록 초기화: 기존 데이터가 있으면 불러오고, 마지막에 빈 칸 추가
        ingredientsList.clear()
        if (recipeData!!.ingredients.isNotEmpty()) {
            ingredientsList.addAll(recipeData!!.ingredients)
        }
        if (ingredientsList.isEmpty() || ingredientsList.last().name?.isNotEmpty() == true) {
            ingredientsList.add(Ingredient())
        }


        // 조리도구 목록 초기화: 기존 데이터가 있으면 불러오고, 마지막에 빈 칸 추가
        toolsList.clear()
        if (recipeData!!.tools.isNotEmpty()) {
            toolsList.addAll(recipeData!!.tools)
        }
        if (toolsList.isEmpty() || toolsList.last().isNotEmpty()) {
            toolsList.add("")
        }
    }

    // RecyclerView 관련 코드 전체 삭제
    private fun setupToolbarAndListeners() {
        utils.ToolbarUtils.setupWriteToolbar(this, "",
            onTempSaveClicked = {
                Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
            },
            onSaveClicked = { }
        )

        binding.btnNext.setOnClickListener { goToNextStep() }
    }

    // 재료 뷰를 동적으로 추가/관리하는 함수
    private fun setupIngredientViews() {
        binding.ingredientsContainer.removeAllViews() // 컨테이너 초기화
        ingredientsList.forEachIndexed { index, ingredient ->
            val ingredientBinding = ItemIngredientBinding.inflate(LayoutInflater.from(this), binding.ingredientsContainer, false)

            ingredientBinding.etIngredientName.setText(ingredient.name)
            ingredientBinding.etIngredientAmount.setText(ingredient.amount)

            // 마지막 항목이 아니면 삭제 버튼 표시
            ingredientBinding.btnDeleteIngredient.visibility = if (index < ingredientsList.size - 1) View.VISIBLE else View.INVISIBLE

            // 텍스트 변경 리스너
            ingredientBinding.etIngredientName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    ingredientsList[index].name = s.toString()
                    // 마지막 칸에 텍스트가 입력되면 새 칸 추가
                    if (index == ingredientsList.size - 1 && s.toString().isNotEmpty()) {
                        addNewIngredientRowIfNeeded()
                    }
                }
            })
            ingredientBinding.etIngredientAmount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    ingredientsList[index].amount = s.toString()
                }
            })

            // 삭제 버튼 리스너
            ingredientBinding.btnDeleteIngredient.setOnClickListener {
                ingredientsList.removeAt(index)
                setupIngredientViews() // 목록을 다시 그림
            }

            binding.ingredientsContainer.addView(ingredientBinding.root)
        }
    }

    // 필요할 때 재료 입력 행을 추가하는 함수
    private fun addNewIngredientRowIfNeeded() {
        if (ingredientsList.lastOrNull()?.name?.isNotEmpty() == true) {
            ingredientsList.add(Ingredient())
            setupIngredientViews() // 새 행이 추가되었으므로 다시 그림
        }
    }

    // 조리도구 뷰를 동적으로 추가/관리하는 함수
    private fun setupToolViews() {
        binding.toolsContainer.removeAllViews() // 컨테이너 초기화
        toolsList.forEachIndexed { index, tool ->
            val toolBinding = ItemToolBinding.inflate(LayoutInflater.from(this), binding.toolsContainer, false)

            toolBinding.etToolName.setText(tool)

            // 마지막 항목이 아니면 삭제 버튼 표시
            toolBinding.btnDeleteTool.visibility = if (index < toolsList.size - 1) View.VISIBLE else View.INVISIBLE

            // 텍스트 변경 리스너
            toolBinding.etToolName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    toolsList[index] = s.toString()
                    // 마지막 칸에 텍스트가 입력되면 새 칸 추가
                    if (index == toolsList.size - 1 && s.toString().isNotEmpty()) {
                        addNewToolRowIfNeeded()
                    }
                }
            })

            // 삭제 버튼 리스너
            toolBinding.btnDeleteTool.setOnClickListener {
                toolsList.removeAt(index)
                setupToolViews() // 목록을 다시 그림
            }

            binding.toolsContainer.addView(toolBinding.root)
        }
    }

    // 필요할 때 조리도구 입력 행을 추가하는 함수
    private fun addNewToolRowIfNeeded() {
        if (toolsList.lastOrNull()?.isNotEmpty() == true) {
            toolsList.add("")
            setupToolViews() // 새 행이 추가되었으므로 다시 그림
        }
    }

    private fun goToNextStep() {
        // 마지막 빈 칸을 제외하고 저장
        val finalIngredients = ingredientsList.filter { !it.name.isNullOrBlank() }
        val finalTools = toolsList.filter { it.isNotBlank() }

        recipeData?.let {
            it.ingredients = finalIngredients
            it.tools = finalTools
            val intent = Intent(this, CookWrite03Activity::class.java).apply {
                putExtra("recipe_data", it)
            }
            startActivity(intent)
        }
    }
}