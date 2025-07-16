package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.RecipeStep_write
import com.example.recipe_pocket.databinding.CookWriteStepBinding

class CookWrite03StepFragment : Fragment() {

    private var _binding: CookWriteStepBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CookWriteViewModel by activityViewModels()
    private var position: Int = -1
    private var currentStep: RecipeStep_write? = null

    private var hour: Int = 0
    private var minute: Int = 0
    private var second: Int = 0 // '초' 변수 추가

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                currentStep?.imageUri = uri.toString()
                binding.ivStepPhoto.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt("position", -1)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CookWriteStepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (position != -1) {
            currentStep = viewModel.steps.value?.getOrNull(position)
            setupUI()
            setupListeners()
        }
    }

    private fun setupUI() {
        currentStep?.let { data ->
            binding.etStepTitle.setText(data.stepTitle)
            binding.etStepDescription.setText(data.stepDescription)

            data.imageUri?.let { uriString ->
                if (uriString.isNotEmpty()) binding.ivStepPhoto.setImageURI(Uri.parse(uriString))
                else binding.ivStepPhoto.setImageResource(R.color.search_color)
            } ?: binding.ivStepPhoto.setImageResource(R.color.search_color)

            binding.writeTimer.isVisible = data.useTimer

            // ViewModel 데이터(총 초)로 hour, minute, second 변수 초기화
            val totalSeconds = data.timerSeconds
            hour = totalSeconds / 3600
            minute = (totalSeconds % 3600) / 60
            second = totalSeconds % 60
            updateTimerDisplay()
            updateTimerButtonText()
        }
    }

    private fun setupListeners() {
        binding.ivStepPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            pickImageLauncher.launch(intent)
        }
        binding.buttonAddTimer.setOnClickListener {
            val newVisibility = !binding.writeTimer.isVisible
            binding.writeTimer.isVisible = newVisibility
            currentStep?.useTimer = newVisibility
            updateTimerButtonText()
        }
        // 버튼 클릭 시 시간/분/초 값 변경 및 순환 로직 적용
        binding.hourP.setOnClickListener { if (hour < 23) hour++; updateTimerDisplay() }
        binding.hourM.setOnClickListener { if (hour > 0) hour--; updateTimerDisplay() }

        binding.minuteP.setOnClickListener { minute = (minute + 1) % 60; updateTimerDisplay() }
        binding.minuteM.setOnClickListener { minute = (minute - 1 + 60) % 60; updateTimerDisplay() }

        binding.secondP.setOnClickListener { second = (second + 1) % 60; updateTimerDisplay() }
        binding.secondM.setOnClickListener { second = (second - 1 + 60) % 60; updateTimerDisplay() }
    }

    private fun updateTimerDisplay() {
        binding.hourNum.text = hour.toString()
        binding.minuteNum.text = minute.toString()
        binding.secondNum.text = second.toString()
    }

    // ViewModel에 최종 데이터를 저장하는 함수
    fun updateViewModelData() {
        currentStep?.apply {
            stepTitle = binding.etStepTitle.text.toString()
            stepDescription = binding.etStepDescription.text.toString()
            // 시간, 분, 초를 총 초로 변환하여 저장
            timerSeconds = this@CookWrite03StepFragment.hour * 3600 + this@CookWrite03StepFragment.minute * 60 + this@CookWrite03StepFragment.second
        }
    }

    private fun updateTimerButtonText() {
        binding.buttonAddTimer.text = if (binding.writeTimer.isVisible) "- 타이머" else "+ 타이머"
    }

    override fun onPause() {
        super.onPause()
        updateViewModelData()
    }

    override fun onDestroyView() {
        updateViewModelData()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(position: Int): CookWrite03StepFragment {
            return CookWrite03StepFragment().apply {
                arguments = Bundle().apply {
                    putInt("position", position)
                }
            }
        }
    }
}