package com.example.recipe_pocket.ui.recipe.read

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.databinding.ItemAllStepsBinding

class AllStepsAdapter(
    private val steps: List<String>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<AllStepsAdapter.StepViewHolder>() {

    inner class StepViewHolder(private val binding: ItemAllStepsBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(stepTitle: String, position: Int) {
            binding.tvStepTitle.text = stepTitle
            binding.root.setOnClickListener {
                onItemClick(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemAllStepsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position], position)
    }

    override fun getItemCount(): Int = steps.size
}