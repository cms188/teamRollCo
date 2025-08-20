package com.example.recipe_pocket.ui.recipe.write

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.recipe_pocket.R
import com.example.recipe_pocket.ai.AITagGenerator
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.databinding.CookWrite03Binding
import com.example.recipe_pocket.databinding.ItemStepIndicatorBinding
import com.example.recipe_pocket.repository.NotificationHandler
import com.example.recipe_pocket.ui.main.MainActivity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.util.*
import kotlinx.coroutines.launch

class CookWrite03Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite03Binding
    private lateinit var stepAdapter: CookWrite03StepAdapter
    private val viewModel: CookWriteViewModel by viewModels()
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite03Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        ViewCompat.setOnApplyWindowInsetsListener(binding.CookWrite03Layout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        (intent.getSerializableExtra("recipe_data") as? RecipeData)?.let {
            viewModel.setInitialRecipeData(it)
        }

        setupViewPager()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupViewPager() {
        stepAdapter = CookWrite03StepAdapter(this)
        binding.viewPagerSteps.adapter = stepAdapter

        binding.viewPagerSteps.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicators(position)
            }
        })
    }

    private fun observeViewModel() {
        viewModel.steps.observe(this, Observer { steps ->
            stepAdapter.submitList(steps.toList()) {
                binding.viewPagerSteps.post {
                    updateIndicators(binding.viewPagerSteps.currentItem)
                }
            }
        })
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener { finish() }
        binding.btnTempSave.setOnClickListener {
            Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
        }
        binding.ivPrevStep.setOnClickListener {
            binding.viewPagerSteps.currentItem -= 1
        }
        binding.ivNextStep.setOnClickListener {
            binding.viewPagerSteps.currentItem += 1
        }
        binding.btnRemoveStep.setOnClickListener {
            if ((viewModel.steps.value?.size ?: 0) > 1) {
                updateAllFragmentsData()
                viewModel.removeStepAt(binding.viewPagerSteps.currentItem)
            }
        }
        binding.btnSave.setOnClickListener {
            saveRecipe()
        }
    }

    private fun updateIndicators(currentPage: Int) {
        val size = viewModel.steps.value?.size ?: 0
        val container = binding.topBarContainer.findViewById<LinearLayout>(R.id.step_indicator_container)
        val scrollView = binding.topBarContainer.findViewById<HorizontalScrollView>(R.id.step_indicator_scroll_view)

        container.removeAllViews()
        var currentStepView: View? = null

        for (i in 0 until size) {
            val itemBinding = ItemStepIndicatorBinding.inflate(LayoutInflater.from(this), container, false)
            itemBinding.tvStepNumber.text = (i + 1).toString()
            itemBinding.tvStepNumber.setTextAppearance(if (i == currentPage) R.style.StepIndicatorActive else R.style.StepIndicatorInactive)
            itemBinding.root.setOnClickListener { binding.viewPagerSteps.currentItem = i }

            if (i == currentPage) {
                currentStepView = itemBinding.root
            }
            container.addView(itemBinding.root)
        }

        val addButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply { marginStart = dpToPx(16) }
            setImageResource(R.drawable.ic_add_orange)
            setBackgroundColor(getColor(android.R.color.transparent))
            setOnClickListener {
                updateAllFragmentsData()
                viewModel.addStep()
                binding.viewPagerSteps.post {
                    binding.viewPagerSteps.setCurrentItem(stepAdapter.itemCount - 1, true)
                }
            }
        }
        container.addView(addButton)

        scrollView.post {
            currentStepView?.let { view ->
                val scrollX = view.left - (scrollView.width / 2) + (view.width / 2)
                scrollView.smoothScrollTo(scrollX, 0)
            }
        }

        binding.ivPrevStep.alpha = if (currentPage > 0) 1.0f else 0.3f
        binding.ivNextStep.alpha = if (currentPage < size - 1) 1.0f else 0.3f
        binding.btnRemoveStep.visibility = if (size > 1) View.VISIBLE else View.GONE
    }

    private fun updateAllFragmentsData() {
        for (i in 0 until (viewModel.steps.value?.size ?: 0)) {
            val fragment = supportFragmentManager.findFragmentByTag("f${stepAdapter.getItemId(i)}") as? CookWrite03StepFragment
            fragment?.updateViewModelData()
        }
    }

    private fun saveRecipe() {
        updateAllFragmentsData()
        val recipeData = viewModel.recipeData.value
        if (recipeData == null) {
            Toast.makeText(this, "저장할 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "태그 생성 중..."

        // AI 태그 생성을 위해 레시피 정보를 조합
        val recipeContentForAI = """
            - 레시피 제목: ${recipeData.title}
            - 간단한 설명: ${recipeData.description}
            - 주요 재료: ${recipeData.ingredients.joinToString(", ") { it.name ?: "" }}
        """.trimIndent()

        // 코루틴을 사용하여 비동기로 API 호출
        lifecycleScope.launch {
            val tagResult = AITagGenerator.generateTags(recipeContentForAI)

            tagResult.onSuccess { generatedTags ->
                // 태그 생성 성공 시, 이미지 업로드 및 최종 저장 단계로 진행
                uploadRecipeAndSave(recipeData, generatedTags)
            }.onFailure { exception ->
                // 태그 생성 실패 시, 사용자에게 알림
                Log.e("AITagGenerator", "태그 생성 실패", exception)
                Toast.makeText(this@CookWrite03Activity, "AI 태그 생성에 실패했습니다. 레시피는 태그 없이 저장됩니다.", Toast.LENGTH_LONG).show()
                // 실패하더라도 레시피 저장은 계속 진행 (태그는 비어있음)
                uploadRecipeAndSave(recipeData, emptySet())
            }
        }
    }

    private fun uploadRecipeAndSave(data: RecipeData, tags: Set<String>) {
        binding.btnSave.isEnabled = false
        binding.btnSave.text = "저장 중..."

        val thumbnailUploadTask = uploadImage(data.thumbnailUrl)

        thumbnailUploadTask.addOnSuccessListener { thumbnailUrl ->
            val stepImageUploadTasks = data.steps.map { uploadImage(it.imageUri) }
            Tasks.whenAllSuccess<String>(stepImageUploadTasks).addOnSuccessListener { stepImageUrls ->
                val firestoreMap = hashMapOf(
                    "userId" to (auth.currentUser?.uid ?: ""),
                    "userEmail" to (auth.currentUser?.email ?: ""),
                    "title" to data.title,
                    "simpleDescription" to data.description,
                    "thumbnailUrl" to (thumbnailUrl ?: ""),
                    "servings" to data.servings,
                    "category" to data.category,
                    "difficulty" to data.difficulty,
                    "cookingTime" to data.cookingTimeMinutes,
                    "ingredients" to data.ingredients.map { mapOf("name" to it.name, "amount" to it.amount, "unit" to it.unit) },
                    "tools" to data.tools,
                    "steps" to data.steps.mapIndexed { index, step ->
                        mapOf(
                            "stepNumber" to (index + 1),
                            "title" to step.stepTitle,
                            "description" to step.stepDescription,
                            "imageUrl" to (stepImageUrls.getOrNull(index) ?: ""),
                            "time" to step.timerSeconds,
                            "useTimer" to step.useTimer
                        )
                    },
                    "tags" to tags.toList(),
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )

                db.collection("Recipes").add(firestoreMap)
                    .addOnSuccessListener {
                        // 레시피 저장 성공 후, 사용자 문서 업데이트
                        val userRef = db.collection("Users").document(auth.currentUser!!.uid)
                        userRef.update("recipeCount", FieldValue.increment(1))
                            .addOnSuccessListener {
                                Log.d("RecipeSave", "recipeCount 업데이트 성공")
                                // 칭호 획득 조건 확인
                                checkAndGrantTitle(userRef)
                            }
                            .addOnFailureListener { e ->
                                Log.w("RecipeSave", "recipeCount 업데이트 실패", e)
                            }

                        // 메인 화면으로 이동
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "데이터 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.btnSave.isEnabled = true
                        binding.btnSave.text = "저장"
                    }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "단계 이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true
                binding.btnSave.text = "저장"
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "대표 이미지 업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "저장"
        }
    }

    /**
     * 칭호 획득 조건을 확인하고, 조건 충족 시 칭호를 부여하는 함수
     * @param userRef 현재 사용자의 Firestore 문서 참조
     */
    private fun checkAndGrantTitle(userRef: DocumentReference) {
        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val currentCount = document.getLong("recipeCount") ?: 0
                var newTitle: String? = null

                // =======================================================
                // ▼▼▼ 칭호 조건과 종류는 여기에서 수정합니다 ▼▼▼
                // =======================================================
                when (currentCount) {
                    3L -> newTitle = "새싹 요리사"
                    10L -> newTitle = "우리집 요리사"
                    25L -> newTitle = "요리 장인"
                    50L -> newTitle = "레시피 마스터"
                }
                // =======================================================

                if (newTitle != null) {
                    // 획득한 칭호 목록(unlockedTitles)에 중복되지 않게 추가
                    userRef.update("unlockedTitles", FieldValue.arrayUnion(newTitle))
                        .addOnSuccessListener {
                            Toast.makeText(applicationContext, "'$newTitle' 칭호를 획득했습니다!", Toast.LENGTH_LONG).show()

                            // 칭호 획득 알림 생성
                            lifecycleScope.launch {
                                auth.currentUser?.uid?.let { userId ->
                                    NotificationHandler.createTitleNotification(userId, newTitle)
                                }
                            }
                        }
                }
            }
        }
    }

    private fun uploadImage(uriString: String?): Task<String?> {
        if (uriString.isNullOrEmpty()) {
            return Tasks.forResult(null)
        }
        val uri = Uri.parse(uriString)
        val storageRef = storage.reference.child("recipe_images/${UUID.randomUUID()}")
        return storageRef.putFile(uri).continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            storageRef.downloadUrl
        }.continueWith { task ->
            if (task.isSuccessful) task.result.toString() else throw task.exception!!
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}