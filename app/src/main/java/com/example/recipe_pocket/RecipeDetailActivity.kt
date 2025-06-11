package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
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
        var hour = recipe.cookingTime!! / 60
        var minute = recipe.cookingTime!! % 60
        if (hour > 0) {
            binding.tvCookingTime.text = recipe.cookingTime?.let { "${hour}시간 ${minute}분" } ?: "정보 없음"
        } else {
            binding.tvCookingTime.text = recipe.cookingTime?.let { "${recipe.cookingTime}분" } ?: "정보 없음"
        }
        binding.tvDifficulty.text = recipe.difficulty ?: "정보 없음"

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

        // --- ⭐ 변경점: 누락되었던 정보 표시 로직 추가 ---

        // 1. 인분 정보 표시 (레이아웃에 tvServings 라는 TextView가 있다고 가정)
        binding.tvServings.text = recipe.servings?.let { "${it}인분" } ?: "정보 없음"

        // 2. 카테고리 정보 표시 (레이아웃에 tvCategory 라는 TextView가 있다고 가정)
        //binding.tvCategory.text = recipe.category ?: "기타"

        // 3. 재료 목록 표시 (레이아웃에 ingredientsContainer 라는 LinearLayout이 있다고 가정)
        /*binding.ingredientsContainer.removeAllViews() // 기존 뷰 모두 제거
        recipe.ingredients?.takeIf { it.isNotEmpty() }?.forEach { ingredient ->
            val ingredientText = "${ingredient.name} ${ingredient.amount}${ingredient.unit}"
            val textView = createListItemTextView(ingredientText)
            binding.ingredientsContainer.addView(textView)
        } ?: run {
            val textView = createListItemTextView("등록된 재료가 없습니다.")
            binding.ingredientsContainer.addView(textView)
        }

        // 4. 조리도구 목록 표시 (레이아웃에 toolsContainer 라는 LinearLayout이 있다고 가정)
        binding.toolsContainer.removeAllViews() // 기존 뷰 모두 제거
        recipe.tools?.takeIf { it.isNotEmpty() }?.forEach { toolName ->
            val textView = createListItemTextView(toolName)
            binding.toolsContainer.addView(textView)
        } ?: run {
            val textView = createListItemTextView("등록된 조리도구가 없습니다.")
            binding.toolsContainer.addView(textView)
        }*/


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

    // ⭐ 변경점: 재료/도구 목록에 동적으로 추가할 TextView를 생성하는 헬퍼 함수
    private fun createListItemTextView(text: String): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            this.text = text
            textSize = 14f // 폰트 크기
            setTextColor(ContextCompat.getColor(context, R.color.black)) // 텍스트 색상
            gravity = Gravity.CENTER_VERTICAL
            val padding = (8 * resources.displayMetrics.density).toInt() // 8dp
            setPadding(padding, padding, padding, padding)
        }
    }
}