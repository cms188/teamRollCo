package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ActivityRecipeMainReadBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeMainReadBinding
    private lateinit var firestore: FirebaseFirestore
    private var recipeId: String? = null
    private var currentRecipe: Recipe? = null // 클래스 멤버로 Recipe 객체 저장

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

    /**
     * RecipeLoader를 사용해 레시피와 작성자 정보를 한 번에 불러오는 함수
     */
    private fun loadRecipeData() {
        lifecycleScope.launch {
            val result = RecipeLoader.loadSingleRecipeWithAuthor(recipeId!!)
            result.fold(
                onSuccess = { recipe ->
                    if (recipe != null) {
                        displayRecipe(recipe)
                    } else {
                        Toast.makeText(this@RecipeDetailActivity, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                },
                onFailure = { e ->
                    Toast.makeText(this@RecipeDetailActivity, "데이터 로딩 실패: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            )
        }
    }

    /**
     * 불러온 Recipe 객체를 사용하여 화면 전체를 구성하는 함수
     */
    private fun displayRecipe(recipe: Recipe) {
        // 현재 레시피 정보를 클래스 멤버 변수에 저장하여 다른 함수에서도 사용
        this.currentRecipe = recipe

        // 작성자 정보 UI 업데이트
        val author = recipe.author
        if (author != null && !recipe.userId.isNullOrEmpty()) {
            // 칭호 표시
            if (!author.title.isNullOrEmpty()) {
                binding.tvAuthorTitle.visibility = View.VISIBLE
                binding.tvAuthorTitle.text = author.title
            } else {
                binding.tvAuthorTitle.visibility = View.GONE
            }
            // 닉네임 표시
            binding.tvAuthorName.text = author.nickname ?: "작성자 정보 없음"

            // 프로필 사진 표시
            if (!author.profileImageUrl.isNullOrEmpty()) {
                Glide.with(this).load(author.profileImageUrl).into(binding.ivAuthorProfile)
            } else {
                binding.ivAuthorProfile.setImageResource(R.drawable.ic_profile_placeholder)
            }

            // 작성자 정보 레이아웃 클릭 리스너
            binding.authorInfoLayout.setOnClickListener {
                val intent = Intent(this, UserFeedActivity::class.java).apply {
                    putExtra(UserFeedActivity.EXTRA_USER_ID, recipe.userId)
                }
                startActivity(intent)
            }
        } else {
            binding.authorInfoLayout.visibility = View.GONE
        }

        // 삭제 버튼 활성화 로직
        val currentUser = Firebase.auth.currentUser
        if (currentUser != null && currentUser.uid == recipe.userId) {
            binding.btnDeleteRecipe.visibility = View.VISIBLE
            binding.btnDeleteRecipe.setOnClickListener {
                showDeleteConfirmationDialog()
            }
        } else {
            binding.btnDeleteRecipe.visibility = View.GONE
        }

        // 나머지 레시피 정보 UI 업데이트
        binding.tvRecipeTitle.text = recipe.title ?: "제목 없음"
        binding.tvRecipeSimpleDescription.text = recipe.simpleDescription ?: "간단 설명 없음"

        val hour = recipe.cookingTime?.div(60) ?: 0
        val minute = recipe.cookingTime?.rem(60) ?: 0
        binding.tvCookingTime.text = if (hour > 0) "${hour}시간 ${minute}분" else "${minute}분"

        binding.tvDifficulty.text = recipe.difficulty ?: "정보 없음"
        binding.tvServings.text = recipe.servings?.let { "${it}인분" } ?: "정보 없음"

        recipe.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let {
            Glide.with(this).load(it).into(binding.ivRecipeThumbnail)
        } ?: binding.ivRecipeThumbnail.setImageResource(R.drawable.bg_no_img_gray)

        binding.tvStepInfo.text = recipe.steps?.let {
            if (it.isNotEmpty()) "${it.size} 단계" else "단계 정보 없음"
        } ?: "단계 정보 없음"

        // 재료 목록 동적 생성
        binding.ingredientsContainer.removeAllViews()
        if (!recipe.ingredients.isNullOrEmpty()) {
            val inflater = LayoutInflater.from(this)
            for (ingredient in recipe.ingredients) {
                val ingredientView = inflater.inflate(R.layout.item_ingredient_display, binding.ingredientsContainer, false)
                val nameTextView = ingredientView.findViewById<TextView>(R.id.ingredient_name)
                val amountTextView = ingredientView.findViewById<TextView>(R.id.ingredient_amount)
                nameTextView.text = ingredient.name ?: ""
                amountTextView.text = "${ingredient.amount ?: ""}${ingredient.unit ?: ""}".trim()
                binding.ingredientsContainer.addView(ingredientView)
            }
        } else {
            val noIngredientsTextView = TextView(this).apply {
                text = "등록된 재료가 없습니다."
                setTextColor(ContextCompat.getColor(context, R.color.text_gray))
                textSize = 15f
            }
            binding.ingredientsContainer.addView(noIngredientsTextView)
        }

        // 조리 도구 목록 동적 생성
        binding.toolsContainer.removeAllViews()
        if (!recipe.tools.isNullOrEmpty()) {
            val inflater = LayoutInflater.from(this)
            for (tool in recipe.tools) {
                val toolView = inflater.inflate(R.layout.item_tool_display, binding.toolsContainer, false)
                val nameTextView = toolView.findViewById<TextView>(R.id.tool_name)
                nameTextView.text = tool.trim()
                binding.toolsContainer.addView(toolView)
            }
        } else {
            val noToolsTextView = TextView(this).apply {
                text = "등록된 조리 도구가 없습니다."
                setTextColor(ContextCompat.getColor(context, R.color.text_gray))
                textSize = 15f
            }
            binding.toolsContainer.addView(noToolsTextView)
        }

        // 하단 버튼 리스너 설정
        binding.btnStartCooking.setOnClickListener {
            val intent = Intent(this, RecipeReadActivity::class.java)
            intent.putExtra("RECIPE_ID", recipeId)
            startActivity(intent)
        }
        binding.btnGoBack.setOnClickListener {
            finish()
        }
    }

    /**
     * 삭제 확인 다이얼로그를 띄우는 함수
     */
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("레시피 삭제")
            .setMessage("이 레시피를 정말로 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                currentRecipe?.id?.let {
                    deleteRecipeProcess(it)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 삭제 전체 프로세스를 관리하는 함수
     */
    private fun deleteRecipeProcess(recipeId: String) {
        val recipeData = currentRecipe ?: return

        deleteRecipeImages(recipeData) { isSuccess ->
            if (isSuccess) {
                firestore.collection("Recipes").document(recipeId).delete()
                    .addOnSuccessListener {
                        updateUserRecipeCount()
                        Toast.makeText(this, "레시피가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "레시피 문서 삭제에 실패했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "이미지 삭제에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 레시피에 포함된 모든 이미지를 Firebase Storage에서 삭제하는 함수
     */
    private fun deleteRecipeImages(recipe: Recipe, onComplete: (Boolean) -> Unit) {
        val storage = Firebase.storage
        val imageTasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()

        recipe.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let {
            imageTasks.add(storage.getReferenceFromUrl(it).delete())
        }
        recipe.steps?.forEach { step ->
            step.imageUrl?.takeIf { it.isNotEmpty() }?.let {
                imageTasks.add(storage.getReferenceFromUrl(it).delete())
            }
        }

        if (imageTasks.isEmpty()) {
            onComplete(true)
            return
        }

        com.google.android.gms.tasks.Tasks.whenAll(imageTasks)
            .addOnSuccessListener {
                Log.d("DeleteRecipe", "모든 이미지를 성공적으로 삭제했습니다.")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("DeleteRecipe", "이미지 삭제 중 오류 발생", e)
                onComplete(false)
            }
    }

    /**
     * 사용자 문서의 recipeCount를 1 감소시키는 함수
     */
    private fun updateUserRecipeCount() {
        val currentUser = Firebase.auth.currentUser ?: return
        val userRef = firestore.collection("Users").document(currentUser.uid)
        userRef.update("recipeCount", FieldValue.increment(-1))
            .addOnSuccessListener {
                Log.d("DeleteRecipe", "recipeCount -1 업데이트 성공")
            }
            .addOnFailureListener { e ->
                Log.w("DeleteRecipe", "recipeCount 업데이트 실패", e)
            }
    }
}