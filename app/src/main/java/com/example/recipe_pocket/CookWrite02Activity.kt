package com.example.recipe_pocket

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.recipe_pocket.databinding.CookWrite02Binding
import com.example.recipe_pocket.databinding.ItemIngredientBinding
import com.example.recipe_pocket.databinding.ItemToolBinding

class CookWrite02Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite02Binding
    private var recipeData: RecipeData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite02Binding.inflate(layoutInflater)
        setContentView(binding.root)

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        // Edge-to-edge 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.CookWrite02Layout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

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

        setupClickListeners()
        addIngredientRow()
        addToolRow()
    }

    // ... (setupClickListeners, addIngredientRow, addToolRow, goToNextStep 로직은 이전과 동일)
    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener { finish() }
        binding.btnTempSave.setOnClickListener {
            Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
        }
        binding.buttonAddIngredient.setOnClickListener { addIngredientRow() }
        binding.buttonAddTool.setOnClickListener { addToolRow() }
        binding.btnNext.setOnClickListener { goToNextStep() }
    }

    private fun addIngredientRow() {
        val itemBinding = ItemIngredientBinding.inflate(layoutInflater, binding.ingredientsContainer, false)
        val unitItems = resources.getStringArray(R.array.ingredient_units)
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, unitItems)
        itemBinding.unitAutocompleteTextview.setAdapter(unitAdapter)
        itemBinding.deleteMaterial.setOnClickListener {
            binding.ingredientsContainer.removeView(itemBinding.root)
        }
        binding.ingredientsContainer.addView(itemBinding.root)
    }

    private fun addToolRow() {
        val itemBinding = ItemToolBinding.inflate(layoutInflater, binding.toolsContainer, false)
        itemBinding.buttonDelete.setOnClickListener {
            binding.toolsContainer.removeView(itemBinding.root)
        }
        binding.toolsContainer.addView(itemBinding.root)
    }

    private fun goToNextStep() {
        val ingredients = mutableListOf<Ingredient>()
        for (i in 0 until binding.ingredientsContainer.childCount) {
            val view = binding.ingredientsContainer.getChildAt(i)
            val name = view.findViewById<EditText>(R.id.a1).text.toString()
            val amount = view.findViewById<EditText>(R.id.a2).text.toString()
            val unit = view.findViewById<AutoCompleteTextView>(R.id.unit_autocomplete_textview).text.toString()
            if (name.isNotBlank()) {
                ingredients.add(Ingredient(name, amount, unit))
            }
        }

        val tools = mutableListOf<String>()
        for (i in 0 until binding.toolsContainer.childCount) {
            val view = binding.toolsContainer.getChildAt(i)
            val name = view.findViewById<EditText>(R.id.b1).text.toString()
            if (name.isNotBlank()) {
                tools.add(name)
            }
        }

        recipeData?.let {
            it.ingredients = ingredients
            it.tools = tools
            val intent = Intent(this, CookWrite03Activity::class.java).apply {
                putExtra("recipe_data", it)
            }
            startActivity(intent)
        }
    }
}