package com.example.recipe_pocket // 실제 패키지명으로 변경

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.recipe_pocket.databinding.ActivityRecipeReadBinding
import com.google.firebase.firestore.FirebaseFirestore
import androidx.viewpager2.widget.ViewPager2

class RecipeReadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeReadBinding
    private lateinit var recipeStepAdapter: RecipeStepAdapter
    private lateinit var firestore: FirebaseFirestore
    private var recipeId: String? = null
    private var totalSteps = 0

    companion object {
        private const val TAG = "RecipeReadActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeReadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra("RECIPE_ID")

        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Recipe ID is null.")
            finish()
            return
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        initFirebase()
        setupViewPager()
        loadRecipeData()
    }

    private fun initFirebase() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupViewPager() {
        recipeStepAdapter = RecipeStepAdapter(emptyList())
        binding.viewPagerRecipeSteps.adapter = recipeStepAdapter

        // ViewPager2 페이지 변경 리스너 등록
        binding.viewPagerRecipeSteps.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 페이지가 선택될 때마다 UI 업데이트
                updateStepProgressIndicator(position + 1, totalSteps)
            }
        })
    }

    private fun loadRecipeData() {
        // recipeId가 null이 아님을 위에서 확인했으므로, 여기서는 !! 사용 가능 (또는 스마트 캐스트 활용)
        firestore.collection("Recipes") // 컬렉션 이름 "Recipes" (대소문자 구분 주의)
            .document(recipeId!!)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val recipe = documentSnapshot.toObject(Recipe::class.java)
                    // recipe가 null이 아닐 때만 displayRecipe 호출 (let 함수 사용)
                    recipe?.let { displayRecipe(it) }
                        ?: run { // recipe가 null일 경우 (변환 실패)
                            Toast.makeText(this, "레시피 정보 변환에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Failed to convert document to Recipe object for ID: $recipeId")
                        }
                } else {
                    Toast.makeText(this, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "No such document with ID: $recipeId")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "데이터 로딩 실패: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error fetching recipe details for ID: $recipeId", e)
            }
    }

    private fun displayRecipe(recipe: Recipe) {
        binding.etSearchBar.setText(recipe.title ?: "레시피 단계")

        if (!recipe.steps.isNullOrEmpty()) {
            // stepNumber 순으로 정렬된 리스트를 어댑터에 전달
            // RecipeStepAdapter 내부에서 정렬을 이미 하고 있다면 여기서 정렬은 생략 가능
            val sortedSteps = recipe.steps.sortedBy { it.stepNumber }
            recipeStepAdapter.updateSteps(sortedSteps)
        } else {
            Toast.makeText(this, "레시피 단계 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Recipe steps are null or empty for ID: $recipeId")
            recipeStepAdapter.updateSteps(emptyList())
        }
        if (!recipe.steps.isNullOrEmpty()) {
            totalSteps = recipe.steps.size // 총 단계 수 저장
            val sortedSteps = recipe.steps.sortedBy { it.stepNumber }
            recipeStepAdapter.updateSteps(sortedSteps)

            // 데이터 로드 후 첫 페이지에 대한 진행도 초기화
            if (totalSteps > 0) {
                updateStepProgressIndicator(1, totalSteps) // 첫 페이지는 1단계
            } else {
                // 단계가 없을 경우 UI 처리
                binding.layoutStepProgressIndicator.visibility = View.GONE
            }
        } else {
            totalSteps = 0
            Toast.makeText(this, "레시피 단계 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Recipe steps are null or empty for ID: $recipeId")
            recipeStepAdapter.updateSteps(emptyList())
            binding.layoutStepProgressIndicator.visibility = View.GONE // 단계 없으면 지표 숨기기
        }
    }
    // 단계 정보 및 진행도 업데이트 함수
    private fun updateStepProgressIndicator(currentStep: Int, totalSteps: Int) {
        if (totalSteps > 0) {
            binding.layoutStepProgressIndicator.visibility = View.VISIBLE // 지표 보이기
            binding.tvCurrentStepTitle.text = "${currentStep}단계"
            binding.tvStepPagerIndicator.text = "$currentStep / $totalSteps"

            // ProgressBar 업데이트 (0부터 시작하는 인덱스가 아닌 1부터 시작하는 단계 기준)
            val progressPercentage = (currentStep.toFloat() / totalSteps.toFloat() * 100).toInt()
            binding.pbStepProgress.progress = progressPercentage
        } else {
            // 단계가 없을 경우 기본값 또는 숨김 처리
            binding.layoutStepProgressIndicator.visibility = View.GONE
        }
    }
}