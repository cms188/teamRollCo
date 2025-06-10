// CookWrite03StepFragment.kt 파일을 아래 코드로 전체 교체하세요.

package com.example.recipe_pocket

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.recipe_pocket.databinding.CookWriteStepBinding

class CookWrite03StepFragment : Fragment() {

    private var _binding: CookWriteStepBinding? = null
    private val binding get() = _binding!!

    private var stepData: RecipeStep? = null
    private var hour = 0
    private var minute = 0

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                stepData?.imageUri = uri.toString() // Uri를 String으로 저장
                binding.ivStepPhoto.setImageURI(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stepData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable("step_data", RecipeStep::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable("step_data") as? RecipeStep
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = CookWriteStepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // ⭐ 변경점: null일 수 있는 stepData를 안전하게 처리하기 위해 let 블록 사용
        stepData?.let { data ->
            data.imageUri?.let { uriString ->
                if (uriString.isNotEmpty()) binding.ivStepPhoto.setImageURI(Uri.parse(uriString))
            }
            binding.etStepTitle.setText(data.stepTitle)
            binding.etStepDescription.setText(data.stepDescription)
            binding.writeTimer.isVisible = data.useTimer
            updateTimerButtonText()
            hour = data.timerMinutes / 60
            minute = data.timerMinutes % 60
            updateTimerDisplay()
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
            stepData?.useTimer = newVisibility // stepData에 변경사항 저장
            updateTimerButtonText()
        }

        binding.hourP.setOnClickListener { hour++; updateTimerDisplay() }
        binding.hourM.setOnClickListener { if (hour > 0) hour--; updateTimerDisplay() }
        binding.minuteP.setOnClickListener { minute = (minute + 5) % 60; updateTimerDisplay() }
        binding.minuteM.setOnClickListener { minute = (minute - 5 + 60) % 60; updateTimerDisplay() }
    }

    private fun updateTimerDisplay() {
        binding.hourNum.setText(hour.toString())
        binding.minuteNum.setText(minute.toString())
    }

    private fun updateTimerButtonText() {
        binding.buttonAddTimer.text = if (binding.writeTimer.isVisible) "- 타이머" else "+ 타이머"
    }

    // ⭐ 변경점: onPause에서도 let 블록을 사용하여 안전하게 데이터 저장
    override fun onPause() {
        super.onPause()
        stepData?.let { data ->
            data.stepTitle = binding.etStepTitle.text.toString()
            data.stepDescription = binding.etStepDescription.text.toString()
            data.timerMinutes = hour * 60 + minute
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(step: RecipeStep): CookWrite03StepFragment {
            return CookWrite03StepFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("step_data", step)
                }
            }
        }
    }
}