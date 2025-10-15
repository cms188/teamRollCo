package com.example.recipe_pocket.ui.recipe.write

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.databinding.CookWrite02Binding

class CookWrite02Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite02Binding
    private var recipeData: RecipeData? = null

    private lateinit var ingredientAdapter: IngredientAdapter
    private lateinit var toolAdapter: ToolAdapter
    private val ingredientsList = mutableListOf<Ingredient>()
    private val toolsList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite02Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        loadInitialData()
        setupRecyclerViews()
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

        ingredientsList.clear()
        if (recipeData!!.ingredients.isNotEmpty()) {
            ingredientsList.addAll(recipeData!!.ingredients)
        }
        ingredientsList.add(Ingredient())

        toolsList.clear()
        if (recipeData!!.tools.isNotEmpty()) {
            toolsList.addAll(recipeData!!.tools)
        }
        toolsList.add("")
    }

    private fun setupRecyclerViews() {
        ingredientAdapter = IngredientAdapter(this, ingredientsList)
        binding.rvIngredients.apply {
            layoutManager = LinearLayoutManager(this@CookWrite02Activity)
            adapter = ingredientAdapter
            itemAnimator = null
        }

        toolAdapter = ToolAdapter(toolsList)
        binding.rvTools.apply {
            layoutManager = LinearLayoutManager(this@CookWrite02Activity)
            adapter = toolAdapter
            itemAnimator = null
        }
    }

    private fun setupToolbarAndListeners() {
        utils.ToolbarUtils.setupWriteToolbar(this, "",
            onTempSaveClicked = {
                Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
            },
            onSaveClicked = { }
        )

        binding.btnNext.setOnClickListener { goToNextStep() }
    }

    private fun goToNextStep() {
        val finalIngredients = ingredientsList.filter { !it.name.isNullOrBlank() }
        val finalTools = toolsList.filter { it.isNotBlank() }

        recipeData?.let {
            it.ingredients = finalIngredients.map { ing -> Ingredient(ing.name, ing.amount, null) }
            it.tools = finalTools
            val intent = Intent(this, CookWrite03Activity::class.java).apply {
                putExtra("recipe_data", it)
            }
            startActivity(intent)
        }
    }
}