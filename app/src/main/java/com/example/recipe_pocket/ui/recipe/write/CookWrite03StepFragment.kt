package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.RecipeStep_write
import com.example.recipe_pocket.databinding.CookWriteStepBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
                val resolver = requireContext().contentResolver
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                try {
                    resolver.takePersistableUriPermission(uri, flag)
                } catch (_: SecurityException) {
                    // Persistable permission might already exist; ignore.
                }
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
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            pickImageLauncher.launch(intent)
        }
        binding.buttonAddTimer.setOnClickListener {
            val newVisibility = !binding.writeTimer.isVisible
            binding.writeTimer.isVisible = newVisibility
            currentStep?.useTimer = newVisibility
            updateTimerButtonText()
        }
        // ▼▼▼ 변경된 부분 ▼▼▼
        // 기존의 +, - 버튼 리스너들을 모두 제거하고, 타이머 표시 TextView의 리스너를 추가합니다.
        binding.tvTimerDisplay.setOnClickListener {
            showTimePickerDialog()
        }
        // ▲▲▲ 변경된 부분 ▲▲▲
    }

    // ▼▼▼ 추가된 함수 ▼▼▼
    private fun showTimePickerDialog() {
        // 새로 만든 dialog_time_picker.xml 레이아웃을 인플레이트합니다.
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_time_picker, null)
        val hourPicker = dialogView.findViewById<NumberPicker>(R.id.picker_hour)
        val minutePicker = dialogView.findViewById<NumberPicker>(R.id.picker_minute)
        val secondPicker = dialogView.findViewById<NumberPicker>(R.id.picker_second)

        // 각 NumberPicker의 설정 (범위, 초기값 등)
        hourPicker.apply {
            minValue = 0
            maxValue = 23
            value = hour
            wrapSelectorWheel = false
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        minutePicker.apply {
            minValue = 0
            maxValue = 59
            value = minute
            wrapSelectorWheel = true // 분/초는 순환되도록 설정
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        secondPicker.apply {
            minValue = 0
            maxValue = 59
            value = second
            wrapSelectorWheel = true // 분/초는 순환되도록 설정
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }

        // MaterialAlertDialog를 사용하여 다이얼로그 생성
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("타이머 설정")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                // "확인" 버튼 클릭 시, NumberPicker에서 선택된 값들을 변수에 저장
                hour = hourPicker.value
                minute = minutePicker.value
                second = secondPicker.value
                // 화면에 변경된 시간을 업데이트
                updateTimerDisplay()
            }
            .setNegativeButton("취소", null)
            .show()
    }
    // ▲▲▲ 추가된 함수 ▲▲▲

    // ▼▼▼ 변경된 함수 ▼▼▼
    private fun updateTimerDisplay() {
        // 시간, 분, 초를 보기 좋은 형식의 문자열로 만들어 tvTimerDisplay에 설정합니다.
        binding.tvTimerDisplay.text = String.format("%02d시간 %02d분 %02d초", hour, minute, second)
    }
    // ▲▲▲ 변경된 함수 ▲▲▲

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
