package com.example.recipe_pocket

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.recipe_pocket.databinding.CookWrite03Binding

class CookWrite03Activity : AppCompatActivity() {

    private lateinit var binding: CookWrite03Binding
    private var recipeData: RecipeData? = null
    private lateinit var stepAdapter: CookWrite03StepAdapter
    private val steps = mutableListOf<RecipeStep>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CookWrite03Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // 02 화면에서 데이터 수신
        recipeData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("recipe_data", RecipeData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("recipe_data") as? RecipeData
        }

        if (recipeData == null) {
            Toast.makeText(this, "데이터 로딩 실패!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViewPager()
        setupClickListeners()
        updateStepIndicatorUI()
    }

    private fun setupViewPager() {
        // 첫 단계 추가
        steps.add(RecipeStep())
        stepAdapter = CookWrite03StepAdapter(this, steps)
        binding.viewPagerSteps.adapter = stepAdapter

        binding.viewPagerSteps.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateStepIndicatorUI()
            }
        })
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener {
            // TODO: "작성을 취소하시겠습니까?" 다이얼로그 추가하면 더 좋음
            finish()
        }

        binding.btnTempSave.setOnClickListener {
            Toast.makeText(this, "임시저장 기능은 아직 지원되지 않습니다.", Toast.LENGTH_SHORT).show()
        }

        binding.ivPrevStep.setOnClickListener {
            if (binding.viewPagerSteps.currentItem > 0) {
                binding.viewPagerSteps.currentItem -= 1
            }
        }

        binding.ivNextStep.setOnClickListener {
            if (binding.viewPagerSteps.currentItem < steps.size - 1) {
                binding.viewPagerSteps.currentItem += 1
            }
        }

        // 최종 저장 버튼
        binding.btnSave.setOnClickListener {
            // Fragment의 onPause가 호출되어 데이터가 저장되도록 페이지를 살짝 변경
            if (steps.isNotEmpty()) {
                val currentItem = binding.viewPagerSteps.currentItem
                if (currentItem > 0) {
                    binding.viewPagerSteps.setCurrentItem(currentItem - 1, false)
                    binding.viewPagerSteps.setCurrentItem(currentItem, false)
                }
            }

            // 최종 데이터 취합
            recipeData?.steps = steps

            // TODO: ViewModel과 Repository를 통해 Firebase에 데이터를 저장하는 로직 구현
            // 현재는 모든 데이터가 제대로 수집되었는지 확인하기 위해 Toast로 출력합니다.
            Toast.makeText(this, "저장 준비 완료!\n${recipeData.toString()}", Toast.LENGTH_LONG).show()

            // TODO: 저장 완료 후 메인 화면으로 이동하거나 현재 액티비티 종료
            // finishAffinity() or startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun updateStepIndicatorUI() {
        val currentPage = binding.viewPagerSteps.currentItem
        binding.stepIndicatorContainer.removeAllViews()

        // 단계 번호들 추가
        for (i in steps.indices) {
            // ⭐ 변경점: View Binding을 사용하여 item_step_indicator.xml을 인플레이트
            val itemBinding = com.example.recipe_pocket.databinding.ItemStepIndicatorBinding.inflate(
                LayoutInflater.from(this),
                binding.stepIndicatorContainer,
                false
            )

            itemBinding.tvStepNumber.text = (i + 1).toString()

            if (i == currentPage) {
                itemBinding.tvStepNumber.setTextAppearance(R.style.StepIndicatorActive)
            } else {
                itemBinding.tvStepNumber.setTextAppearance(R.style.StepIndicatorInactive)
            }

            // 단계 번호를 클릭하면 해당 페이지로 이동
            itemBinding.root.setOnClickListener {
                binding.viewPagerSteps.currentItem = i
            }
            binding.stepIndicatorContainer.addView(itemBinding.root)
        }

        // 단계 추가(+) 버튼 추가
        val addButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24)).apply {
                marginStart = dpToPx(16)
            }
            setImageResource(R.drawable.ic_tab3) // TODO: 적절한 아이콘으로 변경
            setBackgroundColor(getColor(android.R.color.transparent))
            setOnClickListener {
                steps.add(RecipeStep())
                stepAdapter.notifyItemInserted(steps.size - 1)
                binding.viewPagerSteps.currentItem = steps.size - 1
            }
        }
        binding.stepIndicatorContainer.addView(addButton)

        // 좌/우 화살표 활성/비활성
        binding.ivPrevStep.alpha = if (currentPage > 0) 1.0f else 0.3f
        binding.ivNextStep.alpha = if (currentPage < steps.size - 1) 1.0f else 0.3f

        // 단계 삭제 버튼 표시/숨김
        binding.btnRemoveStep.visibility = if (steps.size > 1) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnRemoveStep.setOnClickListener {
            if (steps.size > 1) {
                val positionToRemove = binding.viewPagerSteps.currentItem
                steps.removeAt(positionToRemove)
                stepAdapter.notifyItemRemoved(positionToRemove)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}