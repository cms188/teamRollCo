package com.example.recipe_pocket.ui.recipe.write

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
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
    private val viewHolders = mutableMapOf<Int, StepViewHolder>()

    inner class StepViewHolder(val binding: CookWriteStepBinding) : RecyclerView.ViewHolder(binding.root) {
        var hour: Int = 0
        var minute: Int = 0
        var second: Int = 0

        private val titleWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    steps[adapterPosition] = steps[adapterPosition].copy(title = s.toString())
                }
            }
        }

        private val descriptionWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    steps[adapterPosition] = steps[adapterPosition].copy(description = s.toString())
                }
            }
        }

        fun bind(step: RecipeStep, position: Int) {
            binding.etStepTitle.removeTextChangedListener(titleWatcher)
            binding.etStepDescription.removeTextChangedListener(descriptionWatcher)

            binding.etStepTitle.setText(step.title)
            binding.etStepDescription.setText(step.description)

            if (!step.imageUrl.isNullOrEmpty()) {
                Glide.with(context).load(step.imageUrl).into(binding.ivStepPhoto)
            } else {
                binding.ivStepPhoto.setImageResource(R.color.search_color)
            }

            binding.writeTimer.isVisible = step.useTimer == true

            val totalSeconds = step.time ?: 0
            hour = totalSeconds / 3600
            minute = (totalSeconds % 3600) / 60
            second = totalSeconds % 60
            updateTimerDisplay()

            binding.tvTimerDisplay.setOnClickListener { showTimePickerDialog() }
            binding.buttonAddTimer.setOnClickListener {
                val newVisibility = !binding.writeTimer.isVisible
                binding.writeTimer.isVisible = newVisibility
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    steps[adapterPosition] = steps[adapterPosition].copy(useTimer = newVisibility)
                }
                updateTimerButtonText()
            }
            updateTimerButtonText()

            binding.ivStepPhoto.setOnClickListener { onImageClick(adapterPosition) }
            binding.btnRemoveThisStep.setOnClickListener { onRemoveClick(adapterPosition) }
            binding.btnRemoveThisStep.isVisible = itemCount > 1

            binding.etStepTitle.addTextChangedListener(titleWatcher)
            binding.etStepDescription.addTextChangedListener(descriptionWatcher)
        }

        fun updateTimerDisplay() {
            binding.tvTimerDisplay.text = String.format("%02d시간 %02d분 %02d초", hour, minute, second)
        }

        fun updateTimerButtonText() {
            binding.buttonAddTimer.text = if (binding.writeTimer.isVisible) "- 타이머" else "+ 타이머"
        }

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
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val totalSeconds = hour * 3600 + minute * 60 + second
                        steps[adapterPosition] = steps[adapterPosition].copy(time = totalSeconds)
                    }
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
        val position = viewHolders.entries.find { it.value == holder }?.key
        position?.let { viewHolders.remove(it) }
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
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, steps.size - position)
            updateRemoveButtonVisibility()
        }
    }

    fun updateImageUri(position: Int, uri: Uri) {
        viewHolders[position]?.binding?.ivStepPhoto?.setImageURI(uri)
    }

    fun collectAllStepsData(): List<RecipeStep> {
        return steps.toList()
    }

    private fun updateRemoveButtonVisibility() {
        for (i in 0 until steps.size) {
            notifyItemChanged(i)
        }
    }
}