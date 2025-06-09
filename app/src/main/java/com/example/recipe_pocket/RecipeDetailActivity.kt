package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ActivityRecipeMainReadBinding // 생성된 바인딩 클래스 임포트
import com.google.firebase.firestore.FirebaseFirestore

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeMainReadBinding // 바인딩 객체 선언
    private lateinit var firestore: FirebaseFirestore
    private var recipeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeMainReadBinding.inflate(layoutInflater) // 바인딩 객체 초기화
        setContentView(binding.root) // 바인딩의 root 뷰를 content view로 설정

        ViewCompat.setOnApplyWindowInsetsListener(binding.readRecipeMainLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                //topMargin = insets.top
            }
            WindowInsetsCompat.CONSUMED
        }

        recipeId = intent.getStringExtra("RECIPE_ID")

        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 닫기 버튼 리스너 설정
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
        binding.tvRecipeTitle.text = recipe.title ?: "제목 없음"
        binding.tvRecipeSimpleDescription.text = recipe.simpleDescription ?: "간단 설명 없음"
        binding.tvCookingTime.text = recipe.cookingTime?.let { "${it}분" } ?: "정보 없음"
        binding.tvDifficulty.text = recipe.difficulty ?: "정보 없음"

        recipe.thumbnailUrl?.let {
            if (it.isNotEmpty()) {
                Glide.with(this).load(it).into(binding.ivRecipeThumbnail)
            } else {
                binding.ivRecipeThumbnail.setImageResource(R.drawable.bg_bookmark_shape) // 예시 기본 이미지
            }
        } ?: binding.ivRecipeThumbnail.setImageResource(R.drawable.bg_bookmark_shape) // 예시 기본 이미지

        recipe.steps?.let {
            if (it.isNotEmpty()) {
                binding.tvStepInfo.text = "${it.size} 단계"
            } else {
                binding.tvStepInfo.text = "단계 정보 없음"
            }
        } ?: run {
            binding.tvStepInfo.text = "단계 정보 없음"
        }

        // btnStartCooking, btnGoBack 리스너 설정 (필요한 경우)
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