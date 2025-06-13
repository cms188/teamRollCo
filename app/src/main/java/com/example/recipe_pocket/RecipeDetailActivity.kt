package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ActivityRecipeMainReadBinding
import com.google.firebase.firestore.FirebaseFirestore

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeMainReadBinding
    private lateinit var firestore: FirebaseFirestore
    private var recipeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeMainReadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.readRecipeMainLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        recipeId = intent.getStringExtra("RECIPE_ID")

        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.btnClose.setOnClickListener {
            finish()
        }
        initFirebase()
        loadRecipeData()
    }

    private fun initFirebase() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun loadRecipeData() {
        firestore.collection("Recipes").document(recipeId!!)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val recipe = documentSnapshot.toObject(Recipe::class.java)
                    recipe?.let { displayRecipe(it) }
                } else {
                    Toast.makeText(this, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "데이터 로딩 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun displayRecipe(recipe: Recipe) {
        // --- 기존 정보 표시 ---
        binding.tvRecipeTitle.text = recipe.title ?: "제목 없음"
        binding.tvRecipeSimpleDescription.text = recipe.simpleDescription ?: "간단 설명 없음"
        val hour = recipe.cookingTime?.div(60) ?: 0
        val minute = recipe.cookingTime?.rem(60) ?: 0
        if (hour > 0) {
            binding.tvCookingTime.text = "${hour}시간 ${minute}분"
        } else {
            binding.tvCookingTime.text = "${minute}분"
        }
        binding.tvDifficulty.text = recipe.difficulty ?: "정보 없음"
        binding.tvServings.text = recipe.servings?.let { "${it}인분" } ?: "정보 없음"

        recipe.thumbnailUrl?.let {
            if (it.isNotEmpty()) {
                Glide.with(this).load(it).into(binding.ivRecipeThumbnail)
            } else {
                binding.ivRecipeThumbnail.setImageResource(R.drawable.bg_bookmark_shape)
            }
        } ?: binding.ivRecipeThumbnail.setImageResource(R.drawable.bg_bookmark_shape)

        recipe.steps?.let {
            if (it.isNotEmpty()) {
                binding.tvStepInfo.text = "${it.size} 단계"
            } else {
                binding.tvStepInfo.text = "단계 정보 없음"
            }
        } ?: run {
            binding.tvStepInfo.text = "단계 정보 없음"
        }

        binding.ingredientsContainer.removeAllViews() // 기존 뷰가 있다면 모두 제거

        if (!recipe.ingredients.isNullOrEmpty()) {
            val inflater = LayoutInflater.from(this)
            for (ingredient in recipe.ingredients) {
                // item_ingredient_display.xml을 inflate
                val ingredientView = inflater.inflate(R.layout.item_ingredient_display, binding.ingredientsContainer, false)

                // inflate된 뷰 내부의 TextView들을 찾음
                val nameTextView = ingredientView.findViewById<TextView>(R.id.ingredient_name)
                val amountTextView = ingredientView.findViewById<TextView>(R.id.ingredient_amount)

                // 데이터 설정
                nameTextView.text = ingredient.name ?: ""
                amountTextView.text = "${ingredient.amount ?: ""}${ingredient.unit ?: ""}".trim()

                // 컨테이너에 완성된 뷰 추가
                binding.ingredientsContainer.addView(ingredientView)
            }
        } else {
            // 재료가 없을 경우 표시할 텍스트
            val noIngredientsTextView = TextView(this).apply {
                text = "등록된 재료가 없습니다."
                setTextColor(ContextCompat.getColor(context, R.color.text_gray))
                textSize = 15f
            }
            binding.ingredientsContainer.addView(noIngredientsTextView)
        }

        binding.toolsContainer.removeAllViews() // 기존 뷰가 있다면 모두 제거

        if (!recipe.tools.isNullOrEmpty()) {
            val inflater = LayoutInflater.from(this)
            for (tool in recipe.tools) {
                val toolView = inflater.inflate(R.layout.item_tool_display, binding.toolsContainer, false)
                val nameTextView = toolView.findViewById<TextView>(R.id.tool_name)
                // 데이터 설정
                nameTextView.text = tool.trim()

                // 컨테이너에 완성된 뷰 추가
                binding.toolsContainer.addView(toolView)
            }
        } else {
            // 재료가 없을 경우 표시할 텍스트
            val noToolsTextView = TextView(this).apply {
                text = "등록된 재료가 없습니다."
                setTextColor(ContextCompat.getColor(context, R.color.text_gray))
                textSize = 15f
            }
            binding.toolsContainer.addView(noToolsTextView)
        }


        // --- 버튼 리스너 설정 ---
        binding.btnStartCooking.setOnClickListener {
            val intent = Intent(this, RecipeReadActivity::class.java)
            intent.putExtra("RECIPE_ID", recipeId)
            startActivity(intent)
        }
        binding.btnGoBack.setOnClickListener {
            finish()
        }
    }
}