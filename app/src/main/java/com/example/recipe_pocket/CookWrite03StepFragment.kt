package com.example.recipe_pocket

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
import com.example.recipe_pocket.databinding.CookWriteStepBinding

class CookWrite03StepFragment : Fragment() {

    private var _binding: CookWriteStepBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CookWriteViewModel by activityViewModels()
    private var position: Int = -1
    private var currentStep: RecipeStep_write? = null

    // ▼▼▼ hour, minute 변수를 Fragment가 다시 소유하도록 변경 ▼▼▼
    private var hour: Int = 0
    private var minute: Int = 0

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

            // ViewModel 데이터로 hour, minute 변수 초기화
            hour = data.timerMinutes / 60
            minute = data.timerMinutes % 60
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
        // 버튼 클릭 시 hour, minute 변수를 직접 변경
        binding.hourP.setOnClickListener { if (hour < 23) hour++; updateTimerDisplay() }
        binding.hourM.setOnClickListener { if (hour > 0) hour--; updateTimerDisplay() }
        binding.minuteP.setOnClickListener { if (minute > 59) minute++; updateTimerDisplay() }
        binding.minuteM.setOnClickListener { if (minute > 0) minute--; updateTimerDisplay() }
    }

    // ▼▼▼ UI를 업데이트하는 함수 ▼▼▼
    private fun updateTimerDisplay() {
        binding.hourNum.setText(hour.toString())
        binding.minuteNum.setText(minute.toString())
    }

    // ViewModel에 최종 데이터를 저장하는 함수
    fun updateViewModelData() {
        currentStep?.apply {
            stepTitle = binding.etStepTitle.text.toString()
            stepDescription = binding.etStepDescription.text.toString()
            timerMinutes = this@CookWrite03StepFragment.hour * 60 + this@CookWrite03StepFragment.minute
        }
    }

    private fun updateTimerButtonText() {
        binding.buttonAddTimer.text = if (binding.writeTimer.isVisible) "- 타이머" else "+ 타이머"
    }

    // 화면이 사라지기 전에 ViewModel에 데이터를 저장
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