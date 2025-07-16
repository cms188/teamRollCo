package com.example.recipe_pocket.ui.recipe.write

import android.R
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.RecipeStep
import com.example.recipe_pocket.databinding.ActivityRecipeEditBinding
import com.example.recipe_pocket.databinding.CookWriteStepBinding
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
    private val stepImageUris = mutableMapOf<View, Uri?>()
    private lateinit var thumbnailLauncher: ActivityResultLauncher<Intent>
    private lateinit var stepImageLauncher: ActivityResultLauncher<Intent>
    private var currentStepImageView: ImageView? = null

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
                    currentStepImageView?.let { imageView ->
                        imageView.setImageURI(uri)
                        // 여기서 imageView.rootView.parent는 FrameLayout 이므로, 그 부모인 LinearLayout의 자식인 stepView를 키로 사용해야 합니다.
                        // CookWriteStepBinding의 루트 뷰를 키로 사용하도록 수정합니다.
                        val stepView = imageView.parent.parent as View
                        stepImageUris[stepView] = uri
                    }
                }
            }
        }
    }

    private fun loadRecipeData() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = RecipeLoader.loadSingleRecipeWithAuthor(recipeId!!)
            binding.progressBar.visibility = View.GONE
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
        recipe.steps?.sortedBy { it.stepNumber }?.forEach { addStepView(it) }
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
        binding.buttonAddStep.setOnClickListener { addStepView(null) }
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

    private fun addStepView(step: RecipeStep?) {
        val stepBinding = CookWriteStepBinding.inflate(layoutInflater, binding.stepsContainer, false)
        val stepView = stepBinding.root

        step?.let {
            stepBinding.etStepTitle.setText(it.title)
            stepBinding.etStepDescription.setText(it.description)
            if (!it.imageUrl.isNullOrEmpty()) {
                Glide.with(this).load(it.imageUrl).into(stepBinding.ivStepPhoto)
            }
            stepBinding.writeTimer.visibility = if(it.useTimer == true) View.VISIBLE else View.GONE
            if (it.useTimer == true && it.time != null) {
                val totalSeconds = it.time
                stepBinding.hourNum.text = (totalSeconds / 3600).toString()
                stepBinding.minuteNum.text = ((totalSeconds % 3600) / 60).toString()
                stepBinding.secondNum.text = (totalSeconds % 60).toString()
            }
        }

        stepBinding.ivStepPhoto.setOnClickListener {
            currentStepImageView = stepBinding.ivStepPhoto
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            stepImageLauncher.launch(intent)
        }

        // 각 단계의 삭제 버튼 리스너 설정
        stepBinding.btnRemoveThisStep.setOnClickListener {
            // 최소 1개의 단계는 유지
            if (binding.stepsContainer.childCount > 1) {
                // 이미지 URI 맵에서도 해당 뷰의 정보를 삭제
                stepImageUris.remove(stepView)
                // 부모 뷰(steps_container)에서 이 단계 뷰를 제거
                binding.stepsContainer.removeView(stepView)
                // 삭제 후, 남아있는 단계들의 삭제 버튼 가시성 업데이트
                updateStepRemoveButtonVisibility()
            } else {
                Toast.makeText(this, "최소 한 개의 과정은 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.stepsContainer.addView(stepView)
        // 뷰 추가 후, 모든 단계의 삭제 버튼 가시성 업데이트
        updateStepRemoveButtonVisibility()
    }

    // 단계 삭제 버튼의 가시성을 업데이트하는 함수
    private fun updateStepRemoveButtonVisibility() {
        // 모든 단계 뷰를 순회
        binding.stepsContainer.children.forEach { view ->
            val stepBinding = CookWriteStepBinding.bind(view)
            // 단계가 1개 초과일 때만 삭제 버튼을 보이게 함
            stepBinding.btnRemoveThisStep.isVisible = binding.stepsContainer.childCount > 1
        }
    }


    // --- 저장 로직 ---
    private fun saveChanges() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSave.isEnabled = false

        lifecycleScope.launch {
            try {
                // 1. 이미지 업로드
                val thumbnailUrl = if (thumbnailUri != null) {
                    uploadImage(thumbnailUri, "recipe_images/${UUID.randomUUID()}").await()
                } else {
                    originalRecipe?.thumbnailUrl
                }

                val stepImageUploadTasks = binding.stepsContainer.children.map { stepView ->
                    val uri = stepImageUris[stepView]
                    val originalUrl = originalRecipe?.steps?.find {
                        val stepBinding = CookWriteStepBinding.bind(stepView)
                        it.title == stepBinding.etStepTitle.text.toString() && it.description == stepBinding.etStepDescription.text.toString()
                    }?.imageUrl

                    if (uri != null) {
                        uploadImage(uri, "recipe_images/${UUID.randomUUID()}")
                    } else {
                        Tasks.forResult(originalUrl) // 기존 URL 유지
                    }
                }.toList()

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

                val steps = binding.stepsContainer.children.mapIndexed { index, view ->
                    val stepBinding = CookWriteStepBinding.bind(view)
                    val hour = stepBinding.hourNum.text.toString().toIntOrNull() ?: 0
                    val minute = stepBinding.minuteNum.text.toString().toIntOrNull() ?: 0
                    val second = stepBinding.secondNum.text.toString().toIntOrNull() ?: 0
                    val useTimer = stepBinding.writeTimer.visibility == View.VISIBLE

                    mapOf(
                        "stepNumber" to (index + 1),
                        "title" to stepBinding.etStepTitle.text.toString(),
                        "description" to stepBinding.etStepDescription.text.toString(),
                        "imageUrl" to (stepImageUrls.getOrNull(index) ?: ""),
                        "time" to (hour * 3600 + minute * 60 + second),
                        "useTimer" to useTimer
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
                setResult(RESULT_OK, resultIntent)
                finish()


            } catch (e: Exception) {
                Toast.makeText(this@RecipeEditActivity, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSave.isEnabled = true
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