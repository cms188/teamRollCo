package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.databinding.CookWrite01Binding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CookWrite01Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite01Binding
    private var recipeData = RecipeData() // RecipeData 객체를 멤버 변수로 관리

    private var cookingHour = 0
    private var cookingMinute = 0

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                recipeData.thumbnailUrl = it.toString()
                binding.ivRepresentativePhoto.setImageURI(it)
            }
        }
    }

    // [추가] 카테고리 선택 결과를 처리할 ActivityResultLauncher
    private val categorySelectionLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringArrayListExtra(CategorySelectionActivity.EXTRA_SELECTED_CATEGORIES)?.let { selected ->
                    recipeData.category = selected
                    updateCategoryButtonText()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite01Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        setupWindowInsets()
        setupClickListeners()
        updateServingsText()
        updateCookingTimeText()
        updateCategoryButtonText() // 초기 텍스트 설정
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.CookWrite01Layout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnTempSave.setOnClickListener {
            Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
        }

        binding.ivRepresentativePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            pickImageLauncher.launch(intent)
        }

        // [추가] 카테고리 선택 버튼 클릭 리스너
        binding.btnSelectCategory.setOnClickListener {
            val intent = Intent(this, CategorySelectionActivity::class.java).apply {
                putStringArrayListExtra(CategorySelectionActivity.EXTRA_SELECTED_CATEGORIES, ArrayList(recipeData.category))
            }
            categorySelectionLauncher.launch(intent)
        }

        binding.btnServingsMinus.setOnClickListener {
            if (recipeData.servings > 1) {
                recipeData.servings--
                updateServingsText()
            }
        }
        binding.btnServingsPlus.setOnClickListener {
            recipeData.servings++
            updateServingsText()
        }

        binding.tvCookingTimeHour.setOnClickListener {
            showNumberPickerDialog(isHourPicker = true, currentValue = cookingHour)
        }
        binding.tvCookingTimeMinute.setOnClickListener {
            showNumberPickerDialog(isHourPicker = false, currentValue = cookingMinute)
        }

        binding.btnNext.setOnClickListener {
            if (binding.etRecipeTitle.text.toString().isBlank()) {
                Toast.makeText(this, "레시피 제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            goToNextStep()
        }
    }

    private fun showNumberPickerDialog(isHourPicker: Boolean, currentValue: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_number_picker, null)
        val numberPicker = dialogView.findViewById<NumberPicker>(R.id.number_picker)
        val unitTextView = dialogView.findViewById<TextView>(R.id.tv_unit)

        numberPicker.wrapSelectorWheel = false
        numberPicker.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS

        if (isHourPicker) {
            numberPicker.minValue = 0
            numberPicker.maxValue = 23
            unitTextView.text = "시간"
        } else {
            numberPicker.minValue = 0
            numberPicker.maxValue = 59
            unitTextView.text = "분"
        }
        numberPicker.value = currentValue

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isHourPicker) "시간 선택" else "분 선택")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                if (isHourPicker) {
                    cookingHour = numberPicker.value
                } else {
                    cookingMinute = numberPicker.value
                }
                updateCookingTimeText()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateServingsText() {
        binding.tvServingsCount.text = "${recipeData.servings} 인분"
    }

    private fun updateCookingTimeText() {
        binding.tvCookingTimeHour.text = "$cookingHour 시간"
        binding.tvCookingTimeMinute.text = "$cookingMinute 분"
    }

    // [추가] 선택된 카테고리에 따라 버튼 텍스트 업데이트
    private fun updateCategoryButtonText() {
        if (recipeData.category.isEmpty()) {
            binding.btnSelectCategory.text = "카테고리를 선택해주세요"
            binding.btnSelectCategory.setTextColor(ContextCompat.getColor(this, R.color.text_gray))
        } else {
            binding.btnSelectCategory.text = recipeData.category.joinToString(", ")
            binding.btnSelectCategory.setTextColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    private fun goToNextStep() {
        // 입력된 데이터를 recipeData 객체에 업데이트
        recipeData.title = binding.etRecipeTitle.text.toString()
        recipeData.description = binding.etRecipeDescription.text.toString()
        recipeData.difficulty = when (binding.rgDifficulty.checkedRadioButtonId) {
            R.id.rb_easy -> "쉬움"
            R.id.rb_hard -> "어려움"
            else -> "보통"
        }
        recipeData.cookingTimeMinutes = (cookingHour * 60) + cookingMinute

        val intent = Intent(this, CookWrite02Activity::class.java).apply {
            putExtra("recipe_data", recipeData)
        }
        startActivity(intent)
    }
}