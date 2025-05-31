package com.example.recipe_pocket

import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var ivRecipeThumbnail: ImageView
    private lateinit var tvRecipeTitle: TextView
    private lateinit var tvRecipeSimpleDescription: TextView
    private lateinit var tvRecipeCategory: TextView
    private lateinit var tvRecipeCookingTime: TextView
    private lateinit var tvRecipeDifficulty: TextView
    private lateinit var tvRecipeTools: TextView
    private lateinit var tvRecipeCreatedAt: TextView
    private lateinit var rvRecipeSteps: RecyclerView
    private lateinit var stepAdapter: RecipeStepAdapter

    private lateinit var firestore: FirebaseFirestore
    private var recipeId: String? = null // 이전 화면에서 전달받을 레시피 ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_read)
        //https://developer.android.com/develop/ui/views/layout/edge-to-edge?hl=ko#kotlin
        //동작 모드 또는 버튼 모드에서 시각적 겹침을 방지
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.read_recipe_layout)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                topMargin = insets.top //상단도 마찬가지로 겹침 방지. 꼭 필요한 것은 아님
            }
            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
        // 이전 액티비티에서 레시피 ID를 받아옵니다.
        // 예: intent.getStringExtra("RECIPE_ID")
        recipeId = intent.getStringExtra("RECIPE_ID")

        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_LONG).show()
            finish() // ID가 없으면 액티비티 종료
            return
        }

        initViews()
        initFirebase()
        loadRecipeData()
    }

    private fun initViews() {
        ivRecipeThumbnail = findViewById(R.id.iv_recipe_thumbnail)
        tvRecipeTitle = findViewById(R.id.tv_recipe_title)
        tvRecipeSimpleDescription = findViewById(R.id.tv_recipe_simple_description)
        tvRecipeCategory = findViewById(R.id.tv_recipe_category)
        tvRecipeCookingTime = findViewById(R.id.tv_recipe_cooking_time)
        tvRecipeDifficulty = findViewById(R.id.tv_recipe_difficulty)
        tvRecipeTools = findViewById(R.id.tv_recipe_tools)
        tvRecipeCreatedAt = findViewById(R.id.tv_recipe_created_at)

        rvRecipeSteps = findViewById(R.id.rv_recipe_steps)
        rvRecipeSteps.layoutManager = LinearLayoutManager(this)
        // 스크롤뷰 내의 리사이클러뷰 스크롤 충돌 방지 및 높이 자동 조절을 위해 false로 설정
        rvRecipeSteps.isNestedScrollingEnabled = false
        stepAdapter = RecipeStepAdapter(emptyList())
        rvRecipeSteps.adapter = stepAdapter
    }

    private fun initFirebase() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun loadRecipeData() {
        // Firestore에서 'recipes' 컬렉션의 특정 문서를 ID로 가져옵니다.
        // 컬렉션 이름이 다르다면 수정해주세요. (예: "myRecipes")
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
        tvRecipeTitle.text = recipe.title ?: "제목 없음"
        tvRecipeSimpleDescription.text = recipe.simpleDescription ?: "간단 설명 없음"
        tvRecipeCategory.text = recipe.category ?: "정보 없음"
        tvRecipeCookingTime.text = recipe.cookingTime?.let { "${it}분" } ?: "정보 없음"
        tvRecipeDifficulty.text = recipe.difficulty ?: "정보 없음"

        recipe.thumbnailUrl?.let {
            if (it.isNotEmpty()) {
                Glide.with(this).load(it).into(ivRecipeThumbnail)
            } else {
                ivRecipeThumbnail.setImageResource(R.drawable.ic_launcher_background) // 기본 이미지
            }
        } ?: ivRecipeThumbnail.setImageResource(R.drawable.ic_launcher_background)

        tvRecipeTools.text = recipe.tools?.joinToString(", ") ?: "준비물 정보 없음"

        recipe.createdAt?.let { timestamp ->
            tvRecipeCreatedAt.text = "작성일: ${formatTimestamp(timestamp)}"
        } ?: run {
            tvRecipeCreatedAt.visibility = View.GONE
        }


        // steps 리스트가 null이 아니고 비어있지 않은 경우에만 어댑터에 데이터 설정
        // RecipeStepAdapter 내에서 정렬하므로 여기서는 그냥 전달
        recipe.steps?.let {
            if (it.isNotEmpty()) {
                // stepNumber 기준으로 정렬하여 어댑터에 전달
                stepAdapter.updateSteps(it.sortedBy { step -> step.stepNumber })
            } else {
                rvRecipeSteps.visibility = View.GONE // 단계가 없으면 RecyclerView 숨김
            }
        } ?: run {
            rvRecipeSteps.visibility = View.GONE // 단계 정보가 아예 없으면 숨김
        }
    }

    private fun formatTimestamp(timestamp: Timestamp): String {
        val sdf = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }
}