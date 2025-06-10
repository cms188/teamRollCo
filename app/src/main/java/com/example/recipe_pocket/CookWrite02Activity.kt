package com.example.recipe_pocket

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        // ⭐ 변경점: getSerializableExtra 사용
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
        val unitAdapter = ArrayAdapter.createFromResource(this, R.array.ingredient_units, android.R.layout.simple_spinner_item)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        itemBinding.spinnerUnit.adapter = unitAdapter
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
            val unit = view.findViewById<Spinner>(R.id.spinner_unit).selectedItem.toString()
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