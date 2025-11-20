package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.RecipeData
import com.example.recipe_pocket.databinding.CookWrite01Binding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.recipe_pocket.repository.RecipeSavePipeline
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CookWrite01Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite01Binding
    private var recipeData = RecipeData() // RecipeData 객체를 멤버 변수로 관리

    private var cookingHour = 0
    private var cookingMinute = 0

    private val auth = Firebase.auth

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                recipeData.thumbnailUrl = it.toString()
                displayThumbnail(recipeData.thumbnailUrl)
            }
        }
    }

    private val tempSaveLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            extractRecipeData(result.data)?.let { applyRecipeData(it) }
        }
    }

    private val step2Launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            extractRecipeData(result.data)?.let { applyRecipeData(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite01Binding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        utils.ToolbarUtils.setupWriteToolbar(this, "",
            onTempListClicked = { openTempSaveList() },
            onTempSaveClicked = { tempSaveDraft() },
            onSaveClicked = { }
        )

        CookingBottomMargin()
        setupCategorySelectionResultListener()
        setupClickListeners()
        updateServingsText()
        updateCookingTimeText()
        updateCategoryButtonText() // 초기 텍스트 설정
        disableSaveButton()
    }

    override fun onResume() {
        super.onResume()
        updateTempSaveCount()
    }

    private fun tempSaveDraft() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val current = recipeData.copy(
            title = binding.etRecipeTitle.text.toString(),
            description = binding.etRecipeDescription.text.toString(),
            difficulty = when (binding.rgDifficulty.checkedRadioButtonId) {
                R.id.rb_easy -> "쉬움"
                R.id.rb_hard -> "어려움"
                else -> "보통"
            },
            cookingTimeMinutes = (cookingHour * 60) + cookingMinute
        )

        val tempButton = findViewById<TextView>(R.id.btn_temp_save)
        tempButton?.isEnabled = false
        tempButton?.text = "저장 중..."

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    RecipeSavePipeline.saveDraft(current)
                }
            }
            tempButton?.isEnabled = true
            tempButton?.text = "임시저장"

            if (result.isSuccess) {
                Toast.makeText(this@CookWrite01Activity, "임시저장을 완료했습니다.", Toast.LENGTH_SHORT).show()
                updateTempSaveCount()
            } else {
                Toast.makeText(this@CookWrite01Activity, "임시저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun CookingBottomMargin() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnNext) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = dpToPx(16) + navBottom   // 기본 16dp + 네비게이션바 높이
            }
            insets
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setupClickListeners() {
        binding.representativePhotoFrameLayout.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            pickImageLauncher.launch(intent)
        }

        // 카테고리 선택 버튼 클릭 리스너
        binding.btnSelectCategory.setOnClickListener {
            CategorySelection
                .newInstance(recipeData.category)
                .show(supportFragmentManager, CategorySelection.TAG)
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
            if (recipeData.thumbnailUrl.isNullOrBlank()) {
                Toast.makeText(this, "대표 사진을 등록해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binding.etRecipeTitle.text.toString().isBlank()) {
                Toast.makeText(this, "레시피 제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (binding.etRecipeDescription.text.toString().isBlank()) {
                Toast.makeText(this, "요리 소개를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            goToNextStep()
        }
    }

    private fun setupCategorySelectionResultListener() {
        supportFragmentManager.setFragmentResultListener(
            CategorySelection.REQUEST_KEY,
            this
        ) { _, bundle ->
            bundle.getStringArrayList(CategorySelection.RESULT_SELECTED_CATEGORIES)?.let { selected ->
                recipeData.category = selected
                updateCategoryButtonText()
            }
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

    // 선택된 카테고리에 따라 버튼 텍스트 업데이트
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
        step2Launcher.launch(intent)
    }

    private fun applyRecipeData(newData: RecipeData) {
        recipeData = newData
        displayThumbnail(newData.thumbnailUrl)
        binding.etRecipeTitle.setText(newData.title)
        binding.etRecipeDescription.setText(newData.description)
        recipeData.category = newData.category
        updateCategoryButtonText()

        recipeData.servings = newData.servings
        updateServingsText()

        recipeData.difficulty = newData.difficulty
        val checkedId = when (newData.difficulty) {
            "쉬움" -> R.id.rb_easy
            "어려움" -> R.id.rb_hard
            else -> R.id.rb_normal
        }
        binding.rgDifficulty.check(checkedId)

        cookingHour = newData.cookingTimeMinutes / 60
        cookingMinute = newData.cookingTimeMinutes % 60
        updateCookingTimeText()
    }

    private fun displayThumbnail(uriString: String?) {
        if (uriString.isNullOrBlank()) {
            binding.ivRepresentativePhoto.setImageDrawable(null)
            binding.emptyPhotoState.isVisible = true
            return
        }
        binding.emptyPhotoState.isVisible = false
        binding.ivRepresentativePhoto.isVisible = true
        if (uriString.startsWith("http")) {
            Glide.with(this)
                .load(uriString)
                .centerCrop()
                .placeholder(R.color.search_color)
                .into(binding.ivRepresentativePhoto)
        } else {
            binding.ivRepresentativePhoto.setImageURI(Uri.parse(uriString))
        }
    }


    private fun openTempSaveList() {
        if (auth.currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        tempSaveLauncher.launch(Intent(this, TempSaveListActivity::class.java))
    }

    private fun updateTempSaveCount() {
        val tempButton = findViewById<TextView>(R.id.btn_temp_list) ?: return
        if (auth.currentUser == null) {
            tempButton.isEnabled = false
            utils.ToolbarUtils.updateTempListCount(this, 0)
            return
        }
        tempButton.isEnabled = true
        lifecycleScope.launch {
            val count = runCatching { RecipeSavePipeline.getTempSaveCount() }.getOrElse { 0 }
            utils.ToolbarUtils.updateTempListCount(this@CookWrite01Activity, count)
        }
    }

    private fun extractRecipeData(data: Intent?): RecipeData? {
        if (data == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data.getSerializableExtra("recipe_data", RecipeData::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getSerializableExtra("recipe_data") as? RecipeData
        }
    }

    private fun disableSaveButton() {
        var saveButton = findViewById<TextView>(R.id.btn_save)
        var saveCard = findViewById<CardView>(R.id.save_button_card)

        saveButton.isEnabled = false
        saveCard.isEnabled = false

        saveButton.alpha = 0.4f
        saveButton.setTextColor(getColor(R.color.text_gray))
        saveCard.setCardBackgroundColor(getColor(R.color.primary_divider))
    }
}
