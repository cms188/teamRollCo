package com.example.recipe_pocket.ui.recipe.write

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
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.ActivityRecipeEditBinding
import com.example.recipe_pocket.repository.RecipeLoader
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
    private var selectedCategories: MutableList<String> = mutableListOf()

    // 이미지 URI와 런처 관리
    private var thumbnailUri: Uri? = null
    private lateinit var thumbnailLauncher: ActivityResultLauncher<Intent>
    private lateinit var stepImageLauncher: ActivityResultLauncher<Intent>

    // ViewPager2 관련 변수
    private lateinit var stepAdapter: RecipeEditStepAdapter
    private var currentStepImagePosition: Int = -1
    private val newStepImageUris = mutableMapOf<Int, Uri>()

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
        setupCategorySelectionResultListener()
        setupClickListeners()
        setupViewPager()
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
        selectedCategories.clear()
        recipe.categoryList?.let { selectedCategories.addAll(it) }
        updateCategoryButtonText()

        if (!recipe.thumbnailUrl.isNullOrEmpty()) {
            Glide.with(this).load(recipe.thumbnailUrl).into(binding.ivRepresentativePhoto)
        }
        binding.etRecipeTitle.setText(recipe.title)
        binding.etRecipeDescription.setText(recipe.simpleDescription)

        recipe.ingredients?.forEach { addIngredientRow(it) }
        recipe.tools?.forEach { addToolRow(it) }
        recipe.steps?.let {
            stepAdapter.setSteps(it)
            binding.tvStepIndicator.text = "1 / ${it.size}"
            updateArrowVisibility(0)
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
        binding.ivBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveChanges() }

        binding.btnSelectCategory.setOnClickListener {
            CategorySelection
                .newInstance(selectedCategories)
                .show(supportFragmentManager, CategorySelection.TAG)
        }

        binding.ivRepresentativePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            thumbnailLauncher.launch(intent)
        }
        binding.buttonAddIngredient.setOnClickListener { addIngredientRow(null) }
        binding.buttonAddTool.setOnClickListener { addToolRow(null) }
        binding.buttonAddStep.setOnClickListener {
            stepAdapter.addStep()
            binding.viewPagerSteps.currentItem = stepAdapter.itemCount - 1
        }
        binding.ivPrevStep.setOnClickListener {
            binding.viewPagerSteps.currentItem -= 1
        }
        binding.ivNextStep.setOnClickListener {
            binding.viewPagerSteps.currentItem += 1
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

    private fun addIngredientRow(ingredient: Ingredient?) {
        val itemBinding = com.example.recipe_pocket.databinding.ItemIngredientBinding.inflate(layoutInflater, binding.ingredientsContainer, false)
        val unitItems = resources.getStringArray(R.array.ingredient_units)
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, unitItems)
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
        val itemBinding = com.example.recipe_pocket.databinding.ItemToolBinding.inflate(layoutInflater, binding.toolsContainer, false)
        tool?.let { itemBinding.b1.setText(it) }
        itemBinding.buttonDelete.setOnClickListener {
            binding.toolsContainer.removeView(itemBinding.root)
        }
        binding.toolsContainer.addView(itemBinding.root)
    }

    private fun saveChanges() {
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "저장 중..."

        lifecycleScope.launch {
            try {
                val thumbnailUrl = if (thumbnailUri != null) {
                    uploadImage(thumbnailUri, "recipe_images/${UUID.randomUUID()}").await()
                } else {
                    originalRecipe?.thumbnailUrl
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

                val ingredients = binding.ingredientsContainer.children.mapNotNull {
                    val name = it.findViewById<EditText>(R.id.a1).text.toString()
                    if (name.isBlank()) return@mapNotNull null
                    Ingredient(
                        name,
                        it.findViewById<EditText>(R.id.a2).text.toString(),
                        it.findViewById<AutoCompleteTextView>(R.id.unit_autocomplete_textview).text.toString()
                    )
                }.toList()

                val tools = binding.toolsContainer.children.mapNotNull {
                    val name = it.findViewById<EditText>(R.id.b1).text.toString()
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

                val updatedData = mapOf(
                    "title" to binding.etRecipeTitle.text.toString(),
                    "simpleDescription" to binding.etRecipeDescription.text.toString(),
                    "category" to selectedCategories,
                    "thumbnailUrl" to (thumbnailUrl ?: ""),
                    "ingredients" to ingredients.map { mapOf("name" to it.name, "amount" to it.amount, "unit" to it.unit) },
                    "tools" to tools,
                    "steps" to steps,
                    "updatedAt" to Timestamp.now()
                )

                db.collection("Recipes").document(recipeId!!).update(updatedData).await()

                Toast.makeText(this@RecipeEditActivity, "레시피가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK, Intent())
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@RecipeEditActivity, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "수정완료"
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
