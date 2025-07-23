package com.example.recipe_pocket.ui.recipe.legacy_write

import android.R
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.recipe_pocket.databinding.ActivityRecipeWriteBinding
import com.example.recipe_pocket.databinding.ItemRecipeStepBinding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.IOException
import java.util.UUID

class RecipeWriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeWriteBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var thumbnailUri: Uri? = null
    private val stepImageUris = mutableMapOf<Int, Uri?>()
    private val stepViews = mutableListOf<View>()
    private var currentStepIndex = 0

    private val categories = listOf("한식", "중식", "일식", "양식", "디저트", "기타")
    private val difficulties = listOf("쉬움", "보통", "어려움")

    companion object {
        private const val THUMBNAIL_REQUEST_CODE = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeWriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDropdowns()
        setupButtons()
        addNewStep()
    }

    private fun setupDropdowns() {
        val categoryAdapter = ArrayAdapter(this, R.layout.simple_dropdown_item_1line, categories)
        binding.categoryDropdown.setAdapter(categoryAdapter)

        val difficultyAdapter =
            ArrayAdapter(this, R.layout.simple_dropdown_item_1line, difficulties)
        binding.difficultyDropdown.setAdapter(difficultyAdapter)
    }

    private fun setupButtons() {
        binding.uploadThumbnailBtn.setOnClickListener {
            openImagePicker(THUMBNAIL_REQUEST_CODE)
        }

        binding.addStepBtn.setOnClickListener {
            addNewStep()
            showStep(stepViews.size - 1)
        }

        binding.saveRecipeBtn.setOnClickListener {
            validateAndUploadRecipe()
        }
    }

    private fun addNewStep() {
        val stepBinding = ItemRecipeStepBinding.inflate(layoutInflater, binding.stepsContainer, false)
        val stepNumber = stepViews.size + 1

        stepBinding.stepTextView.text = "단계 $stepNumber"
        stepBinding.stepIndicator.text = "($stepNumber/${stepViews.size + 1})"

        stepBinding.removeStepBtn.setOnClickListener {
            if (stepViews.size > 1) removeStep(stepViews.indexOf(stepBinding.root))
            else Toast.makeText(this, "최소 한 개의 단계가 필요합니다.", Toast.LENGTH_SHORT).show()
        }

        stepBinding.useTimerCheckbox.setOnCheckedChangeListener { _, isChecked ->
            stepBinding.stepTimeInput.isEnabled = isChecked
            stepBinding.stepTimeInputLayout.isEnabled = isChecked
            if (!isChecked) stepBinding.stepTimeInput.text?.clear()
        }

        stepBinding.uploadStepImageBtn.setOnClickListener {
            openImagePicker(stepNumber)
        }

        stepBinding.prevStepBtn.setOnClickListener { showStep(currentStepIndex - 1) }
        stepBinding.nextStepBtn.setOnClickListener { showStep(currentStepIndex + 1) }

        binding.stepsContainer.addView(stepBinding.root)
        stepViews.add(stepBinding.root)
        stepImageUris[stepNumber] = null

        updateAllStepIndicators()
        showStep(stepViews.size - 1)
    }

    private fun removeStep(index: Int) {
        if (index in 0 until stepViews.size) {
            val stepView = stepViews[index]
            binding.stepsContainer.removeView(stepView)
            stepViews.removeAt(index)
            stepImageUris.remove(index + 1)

            val newStepImageUris = mutableMapOf<Int, Uri?>()
            for (i in stepViews.indices) newStepImageUris[i + 1] = stepImageUris[i + 2] ?: null
            stepImageUris.clear()
            stepImageUris.putAll(newStepImageUris)

            for (i in stepViews.indices) {
                val stepBinding = ItemRecipeStepBinding.bind(stepViews[i])
                stepBinding.stepTextView.text = "단계 ${i + 1}"
            }

            updateAllStepIndicators()
            if (currentStepIndex >= stepViews.size) currentStepIndex = stepViews.size - 1
            showStep(currentStepIndex)
        }
    }

    private fun updateAllStepIndicators() {
        for (i in stepViews.indices) {
            val stepBinding = ItemRecipeStepBinding.bind(stepViews[i])
            stepBinding.stepIndicator.text = "(${i + 1}/${stepViews.size})"
        }
    }

    private fun showStep(index: Int) {
        if (index in 0 until stepViews.size) {
            stepViews.forEach { it.visibility = View.GONE }

            currentStepIndex = index
            stepViews[currentStepIndex].visibility = View.VISIBLE

            val stepBinding = ItemRecipeStepBinding.bind(stepViews[currentStepIndex])
            stepBinding.prevStepBtn.isEnabled = currentStepIndex > 0
            stepBinding.nextStepBtn.isEnabled = currentStepIndex < stepViews.size - 1

            stepViews.forEachIndexed { i, view ->
                val binding = ItemRecipeStepBinding.bind(view)
                binding.removeStepBtn.isEnabled = stepViews.size > 1 && i == stepViews.size - 1
            }
        }
    }

    private fun openImagePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return

            if (requestCode == THUMBNAIL_REQUEST_CODE) {
                thumbnailUri = uri
                binding.thumbnailImageView.setImageURI(uri)
            } else {
                val stepNumber = requestCode
                stepImageUris[stepNumber] = uri
                stepViews.getOrNull(stepNumber - 1)?.let {
                    val stepBinding = ItemRecipeStepBinding.bind(it)
                    stepBinding.stepImageView.setImageURI(uri)
                }
            }
        }
    }

    private fun validateAndUploadRecipe() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val category = binding.categoryDropdown.text.toString()
        val title = binding.titleInput.text.toString()
        val cookingTime = binding.cookingTimeInput.text.toString()
        val difficulty = binding.difficultyDropdown.text.toString()
        val simpleDescription = binding.simpleDescriptionInput.text.toString()
        val tools = binding.toolsInput.text.toString()

        if (category.isEmpty() || title.isEmpty() || cookingTime.isEmpty() || difficulty.isEmpty() || simpleDescription.isEmpty()) {
            Toast.makeText(this, "필수 정보를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val steps = mutableListOf<RecipeStep>()
        for (i in stepViews.indices) {
            val stepBinding = ItemRecipeStepBinding.bind(stepViews[i])
            val description = stepBinding.stepDescriptionInput.text.toString()
            val useTimer = stepBinding.useTimerCheckbox.isChecked
            val timeStr = stepBinding.stepTimeInput.text.toString()

            if (description.isEmpty()) {
                Toast.makeText(this, "모든 과정의 설명을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return
            }

            if (useTimer && timeStr.isEmpty()) {
                Toast.makeText(this, "타이머 사용 시 시간 입력이 필요합니다.", Toast.LENGTH_SHORT).show()
                return
            }

            steps.add(
                RecipeStep(
                    stepNumber = i + 1,
                    description = description,
                    time = if (useTimer) timeStr.toIntOrNull() ?: 0 else 0,
                    imageUri = stepImageUris[i + 1],
                    useTimer = useTimer
                )
            )
        }

        val recipe = Recipe(
            userId = currentUser.uid,
            userEmail = currentUser.email ?: "",
            category = category,
            title = title,
            cookingTime = cookingTime.toIntOrNull() ?: 0,
            difficulty = difficulty,
            simpleDescription = simpleDescription,
            tools = tools.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            steps = steps
        )

        uploadRecipeWithImages(recipe)
    }

    private fun uploadRecipeWithImages(recipe: Recipe) {
        binding.saveRecipeBtn.isEnabled = false
        binding.saveRecipeBtn.text = "저장 중..."

        uploadImage(thumbnailUri).addOnCompleteListener { thumbnailTask ->
            if (thumbnailTask.isSuccessful) {
                val thumbnailUrl = thumbnailTask.result ?: ""

                val stepImageTasks = recipe.steps.map { uploadImage(it.imageUri) }

                Tasks.whenAllComplete(stepImageTasks)
                    .addOnCompleteListener {
                        if (it.isSuccessful) {
                            val updatedSteps = recipe.steps.mapIndexed { index, step ->
                                step.copy(imageUrl = stepImageTasks[index].result ?: "")
                            }

                            val now = Timestamp.Companion.now()
                            val recipeData = hashMapOf(
                                "userId" to recipe.userId,
                                "userEmail" to recipe.userEmail,
                                "category" to recipe.category,
                                "title" to recipe.title,
                                "cookingTime" to recipe.cookingTime,
                                "difficulty" to recipe.difficulty,
                                "simpleDescription" to recipe.simpleDescription,
                                "tools" to recipe.tools,
                                "thumbnailUrl" to thumbnailUrl,
                                "createdAt" to now,
                                "updatedAt" to now,
                                "steps" to updatedSteps.map {
                                    hashMapOf(
                                        "stepNumber" to it.stepNumber,
                                        "description" to it.description,
                                        "time" to it.time,
                                        "imageUrl" to it.imageUrl,
                                        "useTimer" to it.useTimer
                                    )
                                }
                            )

                            db.collection("Recipes").add(recipeData)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "레시피가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Firestore 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "과정 이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                        }
                        binding.saveRecipeBtn.isEnabled = true
                        binding.saveRecipeBtn.text = "레시피 저장"
                    }
            } else {
                Toast.makeText(this, "대표 이미지 업로드 실패", Toast.LENGTH_SHORT).show()
                binding.saveRecipeBtn.isEnabled = true
                binding.saveRecipeBtn.text = "레시피 저장"
            }
        }
    }

    private fun uploadImage(uri: Uri?): Task<String> {
        if (uri == null) {
            val source = TaskCompletionSource<String>()
            source.setResult("")
            return source.task
        }

        val fileExtension = contentResolver.getType(uri)?.split("/")?.last() ?: "jpg"
        val storageRef = storage.reference.child("images/${UUID.randomUUID()}.$fileExtension")

        val inputStream = contentResolver.openInputStream(uri)
            ?: return Tasks.forException(IOException("이미지 InputStream 열기 실패"))

        val uploadTask = storageRef.putStream(inputStream)

        return uploadTask
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception ?: Exception("이미지 업로드 실패")
                }
                storageRef.downloadUrl
            }
            .continueWith { task ->
                if (task.isSuccessful) {
                    task.result.toString()
                } else {
                    throw task.exception ?: Exception("다운로드 URL 가져오기 실패")
                }
            }
    }

    data class RecipeStep(
        val stepNumber: Int,
        val description: String,
        val time: Int,
        val imageUri: Uri?,
        val useTimer: Boolean,
        val imageUrl: String = ""
    )

    data class Recipe(
        val userId: String,
        val userEmail: String,
        val category: String,
        val title: String,
        val cookingTime: Int,
        val difficulty: String,
        val simpleDescription: String,
        val tools: List<String>,
        val steps: List<RecipeStep>
    )
}