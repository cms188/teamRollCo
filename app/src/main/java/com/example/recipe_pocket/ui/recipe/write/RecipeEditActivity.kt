package com.example.recipe_pocket.ui.recipe.write

import android.R
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.ActivityRecipeEditBinding
import com.example.recipe_pocket.databinding.ItemIngredientBinding
import com.example.recipe_pocket.databinding.ItemToolBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RecipeEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeEditBinding
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private var recipeId: String? = null
    private var originalRecipe: Recipe? = null

    // 이미지 URI와 런처 관리
    private var thumbnailUri: Uri? = null
    private lateinit var thumbnailLauncher: ActivityResultLauncher<Intent>
    private lateinit var stepImageLauncher: ActivityResultLauncher<Intent>

    // ViewPager2 관련 변수
    private lateinit var stepAdapter: RecipeEditStepAdapter
    private var currentStepImagePosition: Int = -1
    private val newStepImageUris = mutableMapOf<Int, Uri>() // 새로 선택된 이미지 URI 관리

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra("RECIPE_ID")
        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupLaunchers()
        setupClickListeners()
        setupViewPager() // ViewPager 설정 추가
        loadRecipeData()
    }

    private fun setupLaunchers() {
        thumbnailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let {
                    thumbnailUri = it
                    binding.ivRepresentativePhoto.setImageURI(it)
                }
            }
        }

        stepImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    if (currentStepImagePosition != -1) {
                        // 새로 선택된 이미지 URI를 맵에 저장
                        newStepImageUris[currentStepImagePosition] = uri
                        // 어댑터에 알려 UI를 즉시 업데이트
                        stepAdapter.updateImageUri(currentStepImagePosition, uri)
                        currentStepImagePosition = -1 // 리셋
                    }
                }
            }
        }
    }

    private fun setupViewPager() {
        stepAdapter = RecipeEditStepAdapter(this,
            onImageClick = { position ->
                currentStepImagePosition = position
                val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                stepImageLauncher.launch(intent)
            },
            onRemoveClick = { position ->
                stepAdapter.removeStepAt(position)
                // 삭제 후 맵에서도 해당 URI 정보 제거
                newStepImageUris.remove(position)

                updateArrowVisibility(binding.viewPagerSteps.currentItem)
            }
        )

        binding.viewPagerSteps.adapter = stepAdapter

        binding.viewPagerSteps.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.tvStepIndicator.text = "${position + 1} / ${stepAdapter.itemCount}"
                updateArrowVisibility(position)
            }
        })
    }

    private fun loadRecipeData() {
        lifecycleScope.launch {
            val result = RecipeLoader.loadSingleRecipeWithAuthor(recipeId!!)
            result.onSuccess { recipe ->
                if (recipe != null) {
                    originalRecipe = recipe
                    populateUi(recipe)
                } else {
                    Toast.makeText(this@RecipeEditActivity, "레시피 로딩 실패", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }.onFailure {
                Toast.makeText(this@RecipeEditActivity, "오류: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun populateUi(recipe: Recipe) {
        // 기본 정보
        val categories = listOf("한식", "중식", "일식", "양식", "디저트", "음료", "기타")
        val adapter = ArrayAdapter(this, R.layout.simple_dropdown_item_1line, categories)
        binding.categoryDropdown.setAdapter(adapter)
        binding.categoryDropdown.setText(recipe.category, false)

        if (!recipe.thumbnailUrl.isNullOrEmpty()) {
            Glide.with(this).load(recipe.thumbnailUrl).into(binding.ivRepresentativePhoto)
        }
        binding.etRecipeTitle.setText(recipe.title)
        binding.etRecipeDescription.setText(recipe.simpleDescription)

        // 재료
        recipe.ingredients?.forEach { addIngredientRow(it) }
        // 도구
        recipe.tools?.forEach { addToolRow(it) }
        // 단계
        recipe.steps?.let {
            stepAdapter.setSteps(it)
            binding.tvStepIndicator.text = "1 / ${it.size}"
            updateArrowVisibility(0)
        }
    }

    /**
     * 현재 페이지 위치에 따라 좌/우 화살표의 가시성을 조절하는 함수
     */
    private fun updateArrowVisibility(position: Int) {
        val totalSteps = stepAdapter.itemCount
        // 단계가 1개뿐이면 모든 화살표를 숨김
        if (totalSteps <= 1) {
            binding.ivPrevStep.visibility = View.INVISIBLE
            binding.ivNextStep.visibility = View.INVISIBLE
            return
        }

        // 첫 번째 페이지일 경우 왼쪽 화살표 숨김
        binding.ivPrevStep.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        // 마지막 페이지일 경우 오른쪽 화살표 숨김
        binding.ivNextStep.visibility = if (position < totalSteps - 1) View.VISIBLE else View.INVISIBLE
    }


    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveChanges() }

        binding.ivRepresentativePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            thumbnailLauncher.launch(intent)
        }
        binding.buttonAddIngredient.setOnClickListener { addIngredientRow(null) }
        binding.buttonAddTool.setOnClickListener { addToolRow(null) }
        binding.buttonAddStep.setOnClickListener {
            stepAdapter.addStep()
            binding.viewPagerSteps.currentItem = stepAdapter.itemCount - 1 // 마지막 페이지로 이동
        }

        binding.ivPrevStep.setOnClickListener {
            binding.viewPagerSteps.currentItem -= 1
        }
        binding.ivNextStep.setOnClickListener {
            binding.viewPagerSteps.currentItem += 1
        }
    }

    // --- 행 추가 함수들 ---
    private fun addIngredientRow(ingredient: Ingredient?) {
        val itemBinding = ItemIngredientBinding.inflate(layoutInflater, binding.ingredientsContainer, false)
        val unitItems = resources.getStringArray(com.example.recipe_pocket.R.array.ingredient_units)
        val unitAdapter = ArrayAdapter(this, R.layout.simple_dropdown_item_1line, unitItems)
        itemBinding.unitAutocompleteTextview.setAdapter(unitAdapter)

        ingredient?.let {
            itemBinding.a1.setText(it.name)
            itemBinding.a2.setText(it.amount)
            itemBinding.unitAutocompleteTextview.setText(it.unit, false)
        }

        itemBinding.deleteMaterial.setOnClickListener {
            binding.ingredientsContainer.removeView(itemBinding.root)
        }
        binding.ingredientsContainer.addView(itemBinding.root)
    }

    private fun addToolRow(tool: String?) {
        val itemBinding = ItemToolBinding.inflate(layoutInflater, binding.toolsContainer, false)
        tool?.let { itemBinding.b1.setText(it) }
        itemBinding.buttonDelete.setOnClickListener {
            binding.toolsContainer.removeView(itemBinding.root)
        }
        binding.toolsContainer.addView(itemBinding.root)
    }

    // --- 저장 로직 ---
    private fun saveChanges() {
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "저장 중..." // 로딩 상태 표시

        lifecycleScope.launch {
            try {
                // 1. 이미지 업로드
                val thumbnailUrl = if (thumbnailUri != null) {
                    uploadImage(thumbnailUri, "recipe_images/${UUID.randomUUID()}").await()
                } else {
                    originalRecipe?.thumbnailUrl
                }

                val currentStepsData = stepAdapter.collectAllStepsData()

                val stepImageUploadTasks = currentStepsData.mapIndexed { index, step ->
                    val newUri = newStepImageUris[index]
                    if (newUri != null) {
                        // 새 이미지가 있으면 업로드
                        uploadImage(newUri, "recipe_images/${UUID.randomUUID()}")
                    } else {
                        // 없으면 기존 URL 유지
                        Tasks.forResult(step.imageUrl)
                    }
                }

                val stepImageUrls = Tasks.whenAllSuccess<String?>(stepImageUploadTasks).await()


                // 2. 데이터 수집
                val ingredients = binding.ingredientsContainer.children.mapNotNull {
                    val name = it.findViewById<EditText>(com.example.recipe_pocket.R.id.a1).text.toString()
                    if (name.isBlank()) return@mapNotNull null
                    Ingredient(
                        name,
                        it.findViewById<EditText>(com.example.recipe_pocket.R.id.a2).text.toString(),
                        it.findViewById<AutoCompleteTextView>(com.example.recipe_pocket.R.id.unit_autocomplete_textview).text.toString()
                    )
                }.toList()

                val tools = binding.toolsContainer.children.mapNotNull {
                    val name = it.findViewById<EditText>(com.example.recipe_pocket.R.id.b1).text.toString()
                    if (name.isBlank()) return@mapNotNull null
                    name
                }.toList()

                val steps = currentStepsData.mapIndexed { index, stepData ->
                    mapOf(
                        "stepNumber" to (index + 1),
                        "title" to stepData.title,
                        "description" to stepData.description,
                        "imageUrl" to (stepImageUrls.getOrNull(index) ?: ""),
                        "time" to stepData.time,
                        "useTimer" to stepData.useTimer
                    )
                }.toList()

                // 3. Firestore 업데이트
                val updatedData = mapOf(
                    "title" to binding.etRecipeTitle.text.toString(),
                    "simpleDescription" to binding.etRecipeDescription.text.toString(),
                    "category" to binding.categoryDropdown.text.toString(),
                    "thumbnailUrl" to (thumbnailUrl ?: ""),
                    "ingredients" to ingredients.map { mapOf("name" to it.name, "amount" to it.amount, "unit" to it.unit) },
                    "tools" to tools,
                    "steps" to steps,
                    "updatedAt" to Timestamp.now()
                )

                db.collection("Recipes").document(recipeId!!).update(updatedData).await()

                Toast.makeText(this@RecipeEditActivity, "레시피가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                // 수정 완료 후 액티비티 종료
                val resultIntent = Intent()
                setResult(Activity.RESULT_OK, resultIntent)
                finish()


            } catch (e: Exception) {
                Toast.makeText(this@RecipeEditActivity, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "수정완료" // 원래 텍스트로 복구
            }
        }
    }

    private fun uploadImage(uri: Uri?, path: String): Task<String?> {
        if (uri == null) return Tasks.forResult(null)
        val storageRef = storage.reference.child(path)
        return storageRef.putFile(uri).continueWithTask { task ->
            if (!task.isSuccessful) task.exception?.let { throw it }
            storageRef.downloadUrl
        }.continueWith { task ->
            if (task.isSuccessful) task.result.toString() else throw task.exception!!
        }
    }
}