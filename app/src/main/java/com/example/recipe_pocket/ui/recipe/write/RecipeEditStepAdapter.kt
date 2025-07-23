package com.example.recipe_pocket.ui.recipe.write

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.RecipeStep
import com.example.recipe_pocket.databinding.CookWriteStepBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RecipeEditStepAdapter(
    private val context: Context,
    private val onImageClick: (Int) -> Unit,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<RecipeEditStepAdapter.StepViewHolder>() {

    private val steps = mutableListOf<RecipeStep>()
    // ViewHolder를 관리하기 위한 맵
    private val viewHolders = mutableMapOf<Int, StepViewHolder>()

    inner class StepViewHolder(val binding: CookWriteStepBinding) : RecyclerView.ViewHolder(binding.root) {
        // 각 ViewHolder가 자신의 시간, 분, 초를 관리합니다.
        var hour: Int = 0
        var minute: Int = 0
        var second: Int = 0

        fun bind(step: RecipeStep, position: Int) {
            binding.etStepTitle.setText(step.title)
            binding.etStepDescription.setText(step.description)
            if (!step.imageUrl.isNullOrEmpty()) {
                Glide.with(context).load(step.imageUrl).into(binding.ivStepPhoto)
            } else {
                binding.ivStepPhoto.setImageResource(R.color.search_color)
            }

            // 타이머 UI 로직 변경
            binding.writeTimer.isVisible = step.useTimer == true

            // 총 초(time)를 시간, 분, 초로 변환하여 저장
            val totalSeconds = step.time ?: 0
            hour = totalSeconds / 3600
            minute = (totalSeconds % 3600) / 60
            second = totalSeconds % 60
            updateTimerDisplay() // 화면에 시간 표시 업데이트

            // 타이머 시간 표시 TextView 클릭 시 다이얼로그를 띄움
            binding.tvTimerDisplay.setOnClickListener { showTimePickerDialog() }

            // 타이머 추가/제거 버튼 로직
            binding.buttonAddTimer.setOnClickListener {
                val newVisibility = !binding.writeTimer.isVisible
                binding.writeTimer.isVisible = newVisibility
                updateTimerButtonText()
            }
            updateTimerButtonText()


            binding.ivStepPhoto.setOnClickListener { onImageClick(adapterPosition) }
            binding.btnRemoveThisStep.setOnClickListener { onRemoveClick(adapterPosition) }

            // 아이템이 1개 초과일 때만 삭제 버튼 표시
            binding.btnRemoveThisStep.isVisible = itemCount > 1
        }

        // 화면의 타이머 텍스트를 업데이트합니다.
        fun updateTimerDisplay() {
            binding.tvTimerDisplay.text = String.format("%02d시간 %02d분 %02d초", hour, minute, second)
        }

        // "+ 타이머" 버튼 텍스트를 업데이트합니다.
        fun updateTimerButtonText() {
            binding.buttonAddTimer.text = if (binding.writeTimer.isVisible) "- 타이머" else "+ 타이머"
        }

        // 시간 선택 다이얼로그를 보여줍니다.
        private fun showTimePickerDialog() {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null)
            val hourPicker = dialogView.findViewById<NumberPicker>(R.id.picker_hour)
            val minutePicker = dialogView.findViewById<NumberPicker>(R.id.picker_minute)
            val secondPicker = dialogView.findViewById<NumberPicker>(R.id.picker_second)

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
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            }
            secondPicker.apply {
                minValue = 0
                maxValue = 59
                value = second
                wrapSelectorWheel = true
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("타이머 설정")
                .setView(dialogView)
                .setPositiveButton("확인") { _, _ ->
                    hour = hourPicker.value
                    minute = minutePicker.value
                    second = secondPicker.value
                    updateTimerDisplay()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = CookWriteStepBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position], position)
        viewHolders[position] = holder
    }

    override fun onViewRecycled(holder: StepViewHolder) {
        super.onViewRecycled(holder)
        viewHolders.values.remove(holder)
    }

    override fun getItemCount(): Int = steps.size

    fun setSteps(newSteps: List<RecipeStep>) {
        steps.clear()
        steps.addAll(newSteps.sortedBy { it.stepNumber })
        notifyDataSetChanged()
    }

    fun addStep() {
        steps.add(RecipeStep(stepNumber = steps.size + 1, title = "", description = "", useTimer = false, time = 0))
        notifyItemInserted(steps.size - 1)
        updateRemoveButtonVisibility()
    }

    fun removeStepAt(position: Int) {
        if (steps.size > 1) {
            steps.removeAt(position)

            val newViewHolders = mutableMapOf<Int, StepViewHolder>()
            viewHolders.forEach { (key, value) ->
                when {
                    key < position -> newViewHolders[key] = value
                    key > position -> newViewHolders[key - 1] = value
                }
            }
            viewHolders.clear()
            viewHolders.putAll(newViewHolders)

            notifyItemRemoved(position)
            notifyItemRangeChanged(position, steps.size)
            updateRemoveButtonVisibility()
        }
    }

    fun updateImageUri(position: Int, uri: Uri) {
        viewHolders[position]?.binding?.ivStepPhoto?.setImageURI(uri)
    }

    // 현재 뷰들의 데이터를 수집하여 반환하는 함수
    fun collectAllStepsData(): List<RecipeStep> {
        val updatedSteps = mutableListOf<RecipeStep>()
        for (i in 0 until steps.size) {
            val originalStep = steps[i]
            val holder = viewHolders[i]

            val title = holder?.binding?.etStepTitle?.text.toString() ?: originalStep.title
            val description = holder?.binding?.etStepDescription?.text.toString() ?: originalStep.description
            val timeInSeconds = (holder?.hour ?: 0) * 3600 + (holder?.minute ?: 0) * 60 + (holder?.second ?: 0)
            val useTimer = holder?.binding?.writeTimer?.isVisible == true

            updatedSteps.add(
                originalStep.copy(
                    stepNumber = i + 1,
                    title = title,
                    description = description,
                    time = timeInSeconds,
                    useTimer = useTimer
                )
            )
        }
        return updatedSteps
    }

    private fun updateRemoveButtonVisibility() {
        viewHolders.values.forEach { holder ->
            holder.binding.btnRemoveThisStep.isVisible = itemCount > 1
        }
    }
}