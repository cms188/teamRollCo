package com.example.recipe_pocket.ui.recipe.write

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.databinding.CookWrite02Binding
import com.example.recipe_pocket.databinding.ItemIngredientBinding
import com.example.recipe_pocket.databinding.ItemToolBinding

class CookWrite02Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite02Binding
    private var recipeData: RecipeData? = null

    private val ingredientsList = mutableListOf<Ingredient>()
    private val toolsList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite02Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        applyBottomInsetToNextButton()
        loadInitialData()
        setupIngredientViews()
        setupToolViews()
        setupToolbarAndListeners()
    }

    private fun applyBottomInsetToNextButton() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnNext) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = dpToPx(16) + navBottom
            }
            insets
        }
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun loadInitialData() {
        recipeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("recipe_data", RecipeData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("recipe_data") as? RecipeData
        }

        if (recipeData == null) {
            Toast.makeText(this, "데이터 로딩 실패!", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        ingredientsList.clear()
        if (recipeData!!.ingredients.isNotEmpty()) ingredientsList.addAll(recipeData!!.ingredients)
        if (ingredientsList.isEmpty() || ingredientsList.last().name?.isNotEmpty() == true) {
            ingredientsList.add(Ingredient())
        }

        toolsList.clear()
        if (recipeData!!.tools.isNotEmpty()) toolsList.addAll(recipeData!!.tools)
        if (toolsList.isEmpty() || toolsList.last().isNotEmpty()) {
            toolsList.add("")
        }
    }

    private fun setupToolbarAndListeners() {
        utils.ToolbarUtils.setupWriteToolbar(
            this, "",
            onTempSaveClicked = { Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show() },
            onSaveClicked = { }
        )
        binding.btnNext.setOnClickListener { goToNextStep() }
    }

    // 재료 영역

    private fun setupIngredientViews() {
        binding.ingredientsContainer.removeAllViews()
        ingredientsList.forEachIndexed { i, _ ->
            binding.ingredientsContainer.addView(createIngredientRow(i))
        }
    }

    private fun createIngredientRow(index: Int): View {
        val row = ItemIngredientBinding.inflate(
            LayoutInflater.from(this), binding.ingredientsContainer, false
        )

        val item = ingredientsList[index]
        row.etIngredientName.setText(item.name)
        row.etIngredientAmount.setText(item.amount)
        row.btnDeleteIngredient.visibility =
            if (index < ingredientsList.size - 1) View.VISIBLE else View.INVISIBLE

        row.etIngredientName.addTextChangedListener(simpleTextWatcher { s ->
            ingredientsList[index].name = s
            if (index == ingredientsList.size - 1 && s.isNotEmpty()) appendIngredientRow()
        })
        row.etIngredientAmount.addTextChangedListener(simpleTextWatcher { s ->
            ingredientsList[index].amount = s
        })

        row.btnDeleteIngredient.setOnClickListener {
            ingredientsList.removeAt(index)
            setupIngredientViews()
        }

        return row.root
    }

    private fun appendIngredientRow() {
        // 이전 마지막 행의 삭제 버튼 보이기
        val prevIdx = ingredientsList.size - 1
        val prevChild = binding.ingredientsContainer.getChildAt(prevIdx)
        ItemIngredientBinding.bind(prevChild).btnDeleteIngredient.visibility = View.VISIBLE

        ingredientsList.add(Ingredient())
        binding.ingredientsContainer.addView(createIngredientRow(ingredientsList.size - 1))
    }

    // 도구 영역

    private fun setupToolViews() {
        binding.toolsContainer.removeAllViews()
        toolsList.forEachIndexed { i, _ ->
            binding.toolsContainer.addView(createToolRow(i))
        }
    }

    private fun createToolRow(index: Int): View {
        val row = ItemToolBinding.inflate(
            LayoutInflater.from(this), binding.toolsContainer, false
        )

        row.etToolName.setText(toolsList[index])
        row.btnDeleteTool.visibility =
            if (index < toolsList.size - 1) View.VISIBLE else View.INVISIBLE

        row.etToolName.addTextChangedListener(simpleTextWatcher { s ->
            toolsList[index] = s
            if (index == toolsList.size - 1 && s.isNotEmpty()) appendToolRow()
        })

        row.btnDeleteTool.setOnClickListener {
            toolsList.removeAt(index)
            setupToolViews()
        }

        return row.root
    }

    private fun appendToolRow() {
        val prevIdx = toolsList.size - 1
        val prevChild = binding.toolsContainer.getChildAt(prevIdx)
        ItemToolBinding.bind(prevChild).btnDeleteTool.visibility = View.VISIBLE

        toolsList.add("")
        binding.toolsContainer.addView(createToolRow(toolsList.size - 1))
    }

    // 유틸
    private inline fun simpleTextWatcher(crossinline after: (String) -> Unit) =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { after(s?.toString().orEmpty()) }
        }

    private fun goToNextStep() {
        val finalIngredients = ingredientsList.filter { !it.name.isNullOrBlank() }
        val finalTools = toolsList.filter { it.isNotBlank() }

        if (finalIngredients.isEmpty()) {
            Toast.makeText(this, "재료를 1개 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        recipeData?.let {
            it.ingredients = finalIngredients
            it.tools = finalTools
            startActivity(
                Intent(this, CookWrite03Activity::class.java).apply {
                    putExtra("recipe_data", it)
                }
            )
        }
    }
}
