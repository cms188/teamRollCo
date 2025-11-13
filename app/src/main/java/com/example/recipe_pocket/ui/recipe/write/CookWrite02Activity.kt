package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.databinding.CookWrite02Binding
import com.example.recipe_pocket.databinding.ItemIngredientBinding
import com.example.recipe_pocket.databinding.ItemToolBinding
import com.example.recipe_pocket.repository.RecipeSavePipeline
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.recipe_pocket.R

class CookWrite02Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite02Binding
    private var recipeData: RecipeData? = null

    private val ingredientsList = mutableListOf<Ingredient>()
    private val toolsList = mutableListOf<String>()
    private val auth = Firebase.auth

    private val step3Launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            extractRecipeData(result.data)?.let { recipeData = it }
        }
    }

    private val tempSaveLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            extractRecipeData(result.data)?.let { applyRecipeData(it) }
        }
    }

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
        setupSystemBackHandler()
        disableSaveButton()
    }

    override fun onResume() {
        super.onResume()
        updateTempSaveCount()
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
        val initialData = extractRecipeData(intent)
        if (initialData == null) {
            Toast.makeText(this, "데이터 로딩 실패!", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        applyRecipeData(initialData)
    }

    private fun setupToolbarAndListeners() {
        utils.ToolbarUtils.setupWriteToolbar(
            this,
            "",
            onBackPressed = { deliverResultAndFinish() },
            onTempListClicked = { openTempSaveList() },
            onTempSaveClicked = { tempSaveDraft() },
            onSaveClicked = { }
        )
        binding.btnNext.setOnClickListener { goToNextStep() }
    }

    private fun setupSystemBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                deliverResultAndFinish()
            }
        })
    }

    // 재료 영역

    private fun setupIngredientViews() {
        binding.ingredientsContainer.removeAllViews()
        ingredientsList.forEachIndexed { i, _ ->
            binding.ingredientsContainer.addView(createIngredientRow(i))
        }
    }

    private fun tempSaveDraft() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = snapshotRecipeData()

        val tempButton = findViewById<TextView>(R.id.btn_temp_save)
        tempButton?.isEnabled = false
        tempButton?.text = "저장 중..."

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { RecipeSavePipeline.saveDraft(snapshot) }
            }
            tempButton?.isEnabled = true
            tempButton?.text = "임시저장"
            if (result.isSuccess) {
                Toast.makeText(this@CookWrite02Activity, "임시저장을 완료했습니다.", Toast.LENGTH_SHORT).show()
                updateTempSaveCount()
            } else {
                Toast.makeText(this@CookWrite02Activity, "임시저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
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
        val snapshot = snapshotRecipeData()

        if (snapshot.ingredients.isEmpty()) {
            Toast.makeText(this, "재료를 1개 이상 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        recipeData = snapshot
        step3Launcher.launch(
            Intent(this, CookWrite03Activity::class.java).apply {
                putExtra("recipe_data", snapshot)
            }
        )
    }

    private fun applyRecipeData(data: RecipeData) {
        recipeData = data

        ingredientsList.clear()
        data.ingredients.forEach { ingredient ->
            ingredientsList.add(Ingredient(ingredient.name, ingredient.amount, ingredient.unit))
        }
        if (ingredientsList.isEmpty() || !ingredientsList.last().name.isNullOrBlank()) {
            ingredientsList.add(Ingredient())
        }
        setupIngredientViews()

        toolsList.clear()
        toolsList.addAll(data.tools)
        if (toolsList.isEmpty() || toolsList.last().isNotEmpty()) {
            toolsList.add("")
        }
        setupToolViews()
    }

    private fun openTempSaveList() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        tempSaveLauncher.launch(Intent(this, TempSaveListActivity::class.java))
    }

    private fun updateTempSaveCount() {
        val tempListButton = findViewById<TextView>(R.id.btn_temp_list) ?: return
        if (auth.currentUser == null) {
            tempListButton.isEnabled = false
            utils.ToolbarUtils.updateTempListCount(this, 0)
            return
        }
        tempListButton.isEnabled = true
        lifecycleScope.launch {
            val count = runCatching { RecipeSavePipeline.getTempSaveCount() }.getOrElse { 0 }
            utils.ToolbarUtils.updateTempListCount(this@CookWrite02Activity, count)
        }
    }

    private fun extractRecipeData(data: Intent?): RecipeData? {
        if (data == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getSerializableExtra("recipe_data", RecipeData::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getSerializableExtra("recipe_data") as? RecipeData
        }
    }

    private fun deliverResultAndFinish() {
        val snapshot = snapshotRecipeData()
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra("recipe_data", snapshot)
        })
        finish()
    }

    private fun snapshotRecipeData(): RecipeData {
        val finalIngredients = ingredientsList.filter { !it.name.isNullOrBlank() }
        val finalTools = toolsList.filter { it.isNotBlank() }
        val base = recipeData ?: RecipeData()
        return base.copy(
            ingredients = finalIngredients,
            tools = finalTools
        )
    }

    private fun disableSaveButton() {
        var saveButton = findViewById<TextView>(R.id.btn_save)
        var saveCard = findViewById<CardView>(R.id.save_button_card)

        saveButton.isEnabled = false
        saveCard.isEnabled = false

        saveButton.alpha = 0.4f
        saveButton.setTextColor(getColor(R.color.text_gray))
        saveCard.setCardBackgroundColor(getColor(R.color.primary_divider))
    }
}
