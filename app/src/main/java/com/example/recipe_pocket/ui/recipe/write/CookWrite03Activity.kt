package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.viewpager2.widget.ViewPager2
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.databinding.CookWrite03Binding
import com.example.recipe_pocket.databinding.ItemStepIndicatorBinding
import com.example.recipe_pocket.repository.RecipeSavePipeline
import com.example.recipe_pocket.ui.auth.EditProfileActivity
import com.example.recipe_pocket.ui.main.MainActivity
import com.example.recipe_pocket.util.BgSaves
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CookWrite03Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite03Binding
    private lateinit var stepAdapter: CookWrite03StepAdapter
    private val viewModel: CookWriteViewModel by viewModels()
    private val auth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite03Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        (intent.getSerializableExtra("recipe_data") as? RecipeData)?.let {
            viewModel.setInitialRecipeData(it)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                deliverResultAndFinish()
            }
        })

        setupToolbar()
        setupViewPager()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        utils.ToolbarUtils.setupWriteToolbar(
            this,
            "조리 과정",
            onBackPressed = { deliverResultAndFinish() },
            onTempSaveClicked = { tempSaveDraft() },
            onSaveClicked = {
                saveRecipe()
            }
        )
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
    }


    private fun updateIndicators(currentPage: Int) {
        val size = viewModel.steps.value?.size ?: 0
        val container = binding.stepIndicatorContainer
        val scrollView = binding.stepIndicatorScrollView

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

        val recipeSnapshot = buildRecipeSnapshot()
        if (recipeSnapshot == null) {
            Toast.makeText(this, "업로드할 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (auth.currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val saveButton = findViewById<TextView>(R.id.btn_save)
        saveButton?.isEnabled = false
        saveButton?.text = "업로드 준비 중..."

        val displayTitle = recipeSnapshot.title.ifBlank { "레시피" }

        BgSaves.enqueue(
            context = this,
            successMessage = "'$displayTitle' 업로드가 완료되었습니다.",
            failureMessage = "'$displayTitle' 업로드에 실패했습니다."
        ) {
            RecipeSavePipeline.saveRecipeWithAi(recipeSnapshot)
        }

        Toast.makeText(this, "백그라운드에서 업로드를 시작합니다.", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun tempSaveDraft() {
        updateAllFragmentsData()

        val recipeSnapshot = buildRecipeSnapshot()
        if (recipeSnapshot == null) {
            Toast.makeText(this, "업로드할 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (auth.currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val tempButton = findViewById<TextView>(R.id.btn_temp_save)
        tempButton?.isEnabled = false
        tempButton?.text = "저장 중..."

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { RecipeSavePipeline.saveDraft(recipeSnapshot) }
            }
            tempButton?.isEnabled = true
            tempButton?.text = "임시저장"
            if (result.isSuccess) {
                Toast.makeText(this@CookWrite03Activity, "임시저장을 완료했습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@CookWrite03Activity, "임시저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deliverResultAndFinish() {
        updateAllFragmentsData()
        val snapshot = buildRecipeSnapshot()
        if (snapshot != null) {
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("recipe_data", snapshot)
            })
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private fun buildRecipeSnapshot(): RecipeData? {
        val base = viewModel.recipeData.value ?: return null
        val steps = viewModel.steps.value?.map { it.copy() } ?: emptyList()
        val ingredients = base.ingredients.map { it.copy() }
        return base.copy(
            category = base.category.toList(),
            ingredients = ingredients,
            tools = base.tools.toList(),
            steps = steps
        )
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
