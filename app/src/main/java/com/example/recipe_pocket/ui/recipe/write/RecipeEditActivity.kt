package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.ActivityRecipeEditBinding
import com.example.recipe_pocket.databinding.ItemIngredientBinding
import com.example.recipe_pocket.databinding.ItemToolBinding
import com.example.recipe_pocket.repository.RecipeLoader
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class RecipeEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeEditBinding
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private var recipeId: String? = null
    private var originalRecipe: Recipe? = null
    private var selectedCategories: MutableList<String> = mutableListOf()

    private var thumbnailUri: Uri? = null
    private lateinit var thumbnailLauncher: ActivityResultLauncher<Intent>
    private lateinit var stepImageLauncher: ActivityResultLauncher<Intent>

    private lateinit var stepAdapter: RecipeEditStepAdapter
    private var currentStepImagePosition: Int = -1
    private val newStepImageUris = mutableMapOf<Int, Uri>()

    // 데이터를 저장할 리스트
    private val ingredientsList = mutableListOf<Ingredient>()
    private val toolsList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        recipeId = intent.getStringExtra("RECIPE_ID")
        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupLaunchers()
        setupCategorySelectionResultListener()
        setupClickListeners()
        setupViewPager()
        loadRecipeData()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 상단 툴바에 패딩 적용
            binding.toolbarLayout.toolbar.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }
            // 하단 네비게이션 바 등에 가려지지 않도록 패딩 적용 (필요한 경우)
            v.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun populateUi(recipe: Recipe) {
        selectedCategories.clear()
        recipe.categoryList?.let { selectedCategories.addAll(it) }
        updateCategoryButtonText()

        if (!recipe.thumbnailUrl.isNullOrEmpty()) {
            Glide.with(this).load(recipe.thumbnailUrl).into(binding.ivRepresentativePhoto)
        }
        binding.etRecipeTitle.setText(recipe.title)
        binding.etRecipeDescription.setText(recipe.simpleDescription)

        // 재료 목록 초기화 및 뷰 생성
        ingredientsList.clear()
        recipe.ingredients?.let { ingredientsList.addAll(it) }
        // 마지막 항목이 비어있지 않다면 빈 입력칸 추가
        if (ingredientsList.isEmpty() || ingredientsList.last().name?.isNotEmpty() == true) {
            ingredientsList.add(Ingredient())
        }
        setupIngredientViews()

        // 도구 목록 초기화 및 뷰 생성
        toolsList.clear()
        recipe.tools?.let { toolsList.addAll(it) }
        // 마지막 항목이 비어있지 않다면 빈 입력칸 추가
        if (toolsList.isEmpty() || toolsList.last().isNotEmpty()) {
            toolsList.add("")
        }
        setupToolViews()


        recipe.steps?.let {
            stepAdapter.setSteps(it)
            if (it.isNotEmpty()) {
                binding.tvStepIndicator.text = "1 / ${it.size}"
            } else {
                binding.tvStepIndicator.text = "0 / 0"
            }
            updateArrowVisibility(0)
        }
    }

    // --- 재료 뷰 동적 관리 ---
    private fun setupIngredientViews() {
        binding.ingredientsContainer.removeAllViews()
        ingredientsList.forEachIndexed { index, ingredient ->
            val ingredientBinding = ItemIngredientBinding.inflate(LayoutInflater.from(this), binding.ingredientsContainer, false)

            ingredientBinding.etIngredientName.setText(ingredient.name)
            ingredientBinding.etIngredientAmount.setText(ingredient.amount)

            // 마지막 항목(빈 입력칸)이 아니면 삭제 버튼 표시
            ingredientBinding.btnDeleteIngredient.visibility = if (index < ingredientsList.size - 1) View.VISIBLE else View.INVISIBLE

            // 이름 입력 감지
            ingredientBinding.etIngredientName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    ingredientsList[index].name = s.toString()
                    // 마지막 칸에 입력이 시작되면 새 빈 칸 추가
                    if (index == ingredientsList.size - 1 && s.toString().isNotEmpty()) {
                        ingredientsList.add(Ingredient())
                        setupIngredientViews() // 뷰 갱신 (포커스 유지 필요하면 로직 추가 필요)
                    }
                }
            })

            // 양 입력 감지
            ingredientBinding.etIngredientAmount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    ingredientsList[index].amount = s.toString()
                }
            })

            // 삭제 버튼 리스너
            ingredientBinding.btnDeleteIngredient.setOnClickListener {
                ingredientsList.removeAt(index)
                setupIngredientViews() // 뷰 갱신
            }

            binding.ingredientsContainer.addView(ingredientBinding.root)
        }
    }

    // --- 조리도구 뷰 동적 관리 ---
    private fun setupToolViews() {
        binding.toolsContainer.removeAllViews()
        toolsList.forEachIndexed { index, tool ->
            val toolBinding = ItemToolBinding.inflate(LayoutInflater.from(this), binding.toolsContainer, false)

            toolBinding.etToolName.setText(tool)

            // 마지막 항목(빈 입력칸)이 아니면 삭제 버튼 표시
            toolBinding.btnDeleteTool.visibility = if (index < toolsList.size - 1) View.VISIBLE else View.INVISIBLE

            // 입력 감지
            toolBinding.etToolName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    toolsList[index] = s.toString()
                    // 마지막 칸에 입력이 시작되면 새 빈 칸 추가
                    if (index == toolsList.size - 1 && s.toString().isNotEmpty()) {
                        toolsList.add("")
                        setupToolViews() // 뷰 갱신
                    }
                }
            })

            // 삭제 버튼 리스너
            toolBinding.btnDeleteTool.setOnClickListener {
                toolsList.removeAt(index)
                setupToolViews() // 뷰 갱신
            }

            binding.toolsContainer.addView(toolBinding.root)
        }
    }


    private fun saveChanges() {
        binding.toolbarLayout.btnSave.isEnabled = false
        binding.toolbarLayout.btnSave.text = "저장 중..."

        lifecycleScope.launch {
            try {
                val updatedData = mutableMapOf<String, Any>()

                if (thumbnailUri != null) {
                    val thumbnailUrl = uploadImage(thumbnailUri, "recipe_images/${UUID.randomUUID()}").await()
                    updatedData["thumbnailUrl"] = thumbnailUrl ?: ""
                }

                val currentStepsData = stepAdapter.collectAllStepsData()
                val stepImageUploadTasks = currentStepsData.mapIndexed { index, step ->
                    val newUri = newStepImageUris[index]
                    if (newUri != null) {
                        uploadImage(newUri, "recipe_images/${UUID.randomUUID()}")
                    } else {
                        Tasks.forResult(step.imageUrl)
                    }
                }
                val stepImageUrls = Tasks.whenAllSuccess<String?>(stepImageUploadTasks).await()

                // 빈 값 필터링
                val ingredients = ingredientsList.filter { !it.name.isNullOrBlank() }
                    .map { mapOf("name" to it.name, "amount" to it.amount) }
                val tools = toolsList.filter { it.isNotBlank() }

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

                updatedData["title"] = binding.etRecipeTitle.text.toString()
                updatedData["simpleDescription"] = binding.etRecipeDescription.text.toString()
                updatedData["category"] = selectedCategories
                updatedData["ingredients"] = ingredients
                updatedData["tools"] = tools
                updatedData["steps"] = steps
                updatedData["updatedAt"] = Timestamp.now()

                db.collection("Recipes").document(recipeId!!).update(updatedData).await()

                Toast.makeText(this@RecipeEditActivity, "레시피가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK, Intent())
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@RecipeEditActivity, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.toolbarLayout.btnSave.isEnabled = true
                binding.toolbarLayout.btnSave.text = "수정완료"
            }
        }
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
                        newStepImageUris[currentStepImagePosition] = uri
                        stepAdapter.updateImageUri(currentStepImagePosition, uri)
                        currentStepImagePosition = -1
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
                newStepImageUris.remove(position)
                updateArrowVisibility(binding.viewPagerSteps.currentItem)
                binding.tvStepIndicator.text = "${binding.viewPagerSteps.currentItem + 1} / ${stepAdapter.itemCount}"
            }
        )

        binding.viewPagerSteps.adapter = stepAdapter
        binding.viewPagerSteps.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val total = stepAdapter.itemCount
                val current = if (total > 0) position + 1 else 0
                binding.tvStepIndicator.text = "$current / $total"
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

    private fun updateCategoryButtonText() {
        if (selectedCategories.isEmpty()) {
            binding.btnSelectCategory.text = "카테고리를 선택해주세요"
            binding.btnSelectCategory.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
        } else {
            binding.btnSelectCategory.text = selectedCategories.joinToString(", ")
            binding.btnSelectCategory.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun updateArrowVisibility(position: Int) {
        val totalSteps = stepAdapter.itemCount
        binding.ivPrevStep.visibility = if (totalSteps > 1 && position > 0) View.VISIBLE else View.INVISIBLE
        binding.ivNextStep.visibility = if (totalSteps > 1 && position < totalSteps - 1) View.VISIBLE else View.INVISIBLE
    }

    private fun setupClickListeners() {
        // 툴바 버튼 리스너 설정
        binding.toolbarLayout.backButton.setOnClickListener { finish() }
        binding.toolbarLayout.btnSave.setOnClickListener { saveChanges() }

        binding.toolbarLayout.btnTempSave.visibility = View.GONE
        binding.toolbarLayout.toolbarTitle.text = ""
        binding.toolbarLayout.btnSave.text = "수정완료"

        binding.btnSelectCategory.setOnClickListener {
            CategorySelection
                .newInstance(selectedCategories)
                .show(supportFragmentManager, CategorySelection.TAG)
        }

        binding.ivRepresentativePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            thumbnailLauncher.launch(intent)
        }

        binding.buttonAddStep.setOnClickListener {
            stepAdapter.addStep()
            // 새 단계 추가 후 마지막 페이지로 이동
            binding.viewPagerSteps.post {
                binding.viewPagerSteps.setCurrentItem(stepAdapter.itemCount - 1, true)
            }
        }
        binding.ivPrevStep.setOnClickListener {
            val current = binding.viewPagerSteps.currentItem
            if (current > 0) {
                binding.viewPagerSteps.setCurrentItem(current - 1, true)
            }
        }
        binding.ivNextStep.setOnClickListener {
            val current = binding.viewPagerSteps.currentItem
            if (current < stepAdapter.itemCount - 1) {
                binding.viewPagerSteps.setCurrentItem(current + 1, true)
            }
        }
    }

    private fun setupCategorySelectionResultListener() {
        supportFragmentManager.setFragmentResultListener(
            CategorySelection.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getStringArrayList(CategorySelection.RESULT_SELECTED_CATEGORIES)?.let { selected ->
                selectedCategories.clear()
                selectedCategories.addAll(selected)
                updateCategoryButtonText()
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