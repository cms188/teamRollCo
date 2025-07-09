package com.example.recipe_pocket

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.recipe_pocket.databinding.CookWrite01Binding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CookWrite01Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite01Binding
    private var currentServings = 1
    private var thumbnailUri: Uri? = null

    private var cookingHour = 0
    private var cookingMinute = 0

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                thumbnailUri = it
                binding.ivRepresentativePhoto.setImageURI(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ▼▼▼ cook_write_01.xml로 바인딩 클래스 이름 변경 ▼▼▼
        binding = CookWrite01Binding.inflate(layoutInflater)
        setContentView(binding.root)

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        // Edge-to-edge 처리
        ViewCompat.setOnApplyWindowInsetsListener(binding.CookWrite01Layout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        setupDropdown()
        setupClickListeners()
        updateServingsText()
        updateCookingTimeText()
    }

    private fun setupDropdown() {
        val categories = listOf("한식", "중식", "일식", "양식", "디저트", "음료", "기타")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.categoryDropdown.setAdapter(adapter)
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnTempSave.setOnClickListener {
            Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
        }

        binding.ivRepresentativePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        binding.btnServingsMinus.setOnClickListener {
            if (currentServings > 1) {
                currentServings--
                updateServingsText()
            }
        }
        binding.btnServingsPlus.setOnClickListener {
            currentServings++
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
        binding.tvServingsCount.text = "$currentServings 인분"
    }

    private fun updateCookingTimeText() {
        binding.tvCookingTimeHour.text = "$cookingHour 시간"
        binding.tvCookingTimeMinute.text = "$cookingMinute 분"
    }

    private fun goToNextStep() {
        val totalCookingTimeMinutes = (cookingHour * 60) + cookingMinute

        val recipeData = RecipeData(
            thumbnailUrl = thumbnailUri?.toString(),
            category = binding.categoryDropdown.text.toString().ifEmpty { "기타" },
            title = binding.etRecipeTitle.text.toString(),
            description = binding.etRecipeDescription.text.toString(),
            difficulty = when (binding.rgDifficulty.checkedRadioButtonId) {
                R.id.rb_easy -> "쉬움"
                R.id.rb_hard -> "어려움"
                else -> "보통"
            },
            servings = currentServings,
            cookingTimeMinutes = totalCookingTimeMinutes
        )

        val intent = Intent(this, CookWrite02Activity::class.java).apply {
            putExtra("recipe_data", recipeData)
        }
        startActivity(intent)
    }
}