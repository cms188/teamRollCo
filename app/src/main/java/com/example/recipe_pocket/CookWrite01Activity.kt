package com.example.recipe_pocket

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.recipe_pocket.databinding.CookWrite01Binding

class CookWrite01Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite01Binding
    private var currentServings = 1
    private var thumbnailUri: Uri? = null

    // 대표 사진 선택을 위한 ActivityResultLauncher
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
        binding = CookWrite01Binding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDropdown()
        setupClickListeners()
        updateServingsText()
    }

    private fun setupDropdown() {
        val categories = listOf("한식", "중식", "일식", "양식", "디저트", "기타")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.categoryDropdown.setAdapter(adapter)
        // 기본값으로 첫 번째 아이템을 설정하고 싶다면 아래 주석을 해제하세요.
        // binding.categoryDropdown.setText(categories[0], false)
    }

    private fun setupClickListeners() {
        // 뒤로가기
        binding.ivBack.setOnClickListener {
            // TODO: "작성을 취소하시겠습니까?" 다이얼로그 추가하면 더 좋음
            finish()
        }

        // 임시저장 (단순 알림)
        binding.btnTempSave.setOnClickListener {
            Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
        }

        // 대표 사진 선택
        binding.ivRepresentativePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            pickImageLauncher.launch(intent)
        }

        // 인원 조절
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

        // 조리 시간 (현재는 Toast 메시지만 표시)
        binding.tvCookingTimeHour.setOnClickListener {
            Toast.makeText(this, "시간 설정 기능 구현 필요", Toast.LENGTH_SHORT).show()
            // TODO: NumberPickerDialog 등을 띄워 시간 설정 로직 구현
        }
        binding.tvCookingTimeMinute.setOnClickListener {
            Toast.makeText(this, "분 설정 기능 구현 필요", Toast.LENGTH_SHORT).show()
            // TODO: NumberPickerDialog 등을 띄워 분 설정 로직 구현
        }

        // 다음 버튼
        binding.btnNext.setOnClickListener {
            if (binding.etRecipeTitle.text.toString().isBlank()) {
                Toast.makeText(this, "레시피 제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            goToNextStep()
        }
    }

    private fun updateServingsText() {
        binding.tvServingsCount.text = "$currentServings 인분"
    }

    private fun goToNextStep() {
        val recipeData = RecipeData(
            thumbnailUrl = thumbnailUri?.toString(), // Uri를 String으로 변환하여 저장
            category = binding.categoryDropdown.text.toString().ifEmpty { "기타" },
            title = binding.etRecipeTitle.text.toString(),
            description = binding.etRecipeDescription.text.toString(),
            difficulty = when (binding.rgDifficulty.checkedRadioButtonId) {
                R.id.rb_easy -> "쉬움"
                R.id.rb_hard -> "어려움"
                else -> "보통"
            },
            servings = currentServings
            // cookingTimeMinutes 는 다이얼로그 구현 후 설정
        )

        val intent = Intent(this, CookWrite02Activity::class.java).apply {
            putExtra("recipe_data", recipeData)
        }
        startActivity(intent)
    }
}