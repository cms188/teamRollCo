package com.example.recipe_pocket

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.viewpager2.widget.ViewPager2
import com.example.recipe_pocket.databinding.CookWrite03Binding
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.util.*

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
                // 페이지가 선택되면, 인디케이터 UI만 갱신
                updateIndicators(position)
            }
        })
    }

    private fun observeViewModel() {
        viewModel.steps.observe(this, Observer { steps ->
            // ViewModel의 steps 데이터가 변경될 때만 어댑터에 리스트를 전달
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

    // 인디케이터와 버튼 상태만 업데이트하는 함수
    private fun updateIndicators(currentPage: Int) {
        val size = viewModel.steps.value?.size ?: 0
        // ID가 중복될 수 있으므로, 정확한 컨테이너를 찾기 위해 topBarContainer를 통해 접근
        val container = binding.topBarContainer.findViewById<LinearLayout>(R.id.step_indicator_container)
        val scrollView = binding.topBarContainer.findViewById<HorizontalScrollView>(R.id.step_indicator_scroll_view)

        container.removeAllViews()
        var currentStepView: View? = null

        for (i in 0 until size) {
            val itemBinding = com.example.recipe_pocket.databinding.ItemStepIndicatorBinding.inflate(LayoutInflater.from(this), container, false)
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
                // 리스트가 업데이트 된 후 마지막 페이지로 이동
                binding.viewPagerSteps.post {
                    binding.viewPagerSteps.setCurrentItem(stepAdapter.itemCount - 1, true)
                }
            }
        }
        container.addView(addButton)

        // ▼▼▼ 자동 스크롤 로직을 post 블록 안으로 이동 ▼▼▼
        // 뷰가 그려진 후(레이아웃 패스가 끝난 후)에 실행되도록 post를 사용
        scrollView.post {
            currentStepView?.let { view ->
                val scrollX = view.left - (scrollView.width / 2) + (view.width / 2)
                scrollView.smoothScrollTo(scrollX, 0)
            }
        }

        // 좌우 화살표 및 삭제 버튼 상태 업데이트 (이 부분은 그대로 유지)
        binding.ivPrevStep.alpha = if (currentPage > 0) 1.0f else 0.3f
        binding.ivNextStep.alpha = if (currentPage < size - 1) 1.0f else 0.3f
        binding.btnRemoveStep.visibility = if (size > 1) View.VISIBLE else View.GONE
    }

    private fun updateAllFragmentsData() {
        for (i in 0 until (viewModel.steps.value?.size ?: 0)) {
            // FragmentStateAdapter는 'f' + itemId 형식의 태그를 사용
            val fragment = supportFragmentManager.findFragmentByTag("f${stepAdapter.getItemId(i)}") as? CookWrite03StepFragment
            fragment?.updateViewModelData()
        }
    }

    private fun saveRecipe() {
        updateAllFragmentsData()

        val recipeData = viewModel.recipeData.value
        val steps = viewModel.steps.value

        if (recipeData == null || steps == null) {
            Toast.makeText(this, "저장할 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        recipeData.steps = steps
        uploadRecipeAndSave(recipeData)
    }

    private fun uploadRecipeAndSave(data: RecipeData) {
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
                            "time" to step.timerMinutes,
                            "useTimer" to step.useTimer
                        )
                    },
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )

                db.collection("Recipes").add(firestoreMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "레시피가 저장되었습니다.", Toast.LENGTH_SHORT).show()
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