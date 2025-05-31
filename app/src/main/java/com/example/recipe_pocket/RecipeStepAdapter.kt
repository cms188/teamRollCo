package com.example.recipe_pocket

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // Glide 사용 (build.gradle에 추가 필요)

class RecipeStepAdapter(private var steps: List<RecipeStep>) :
    RecyclerView.Adapter<RecipeStepAdapter.StepViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.read_recipe_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        // stepNumber 순으로 정렬된 리스트를 받았다고 가정
        val step = steps[position]
        holder.bind(step)
    }

    override fun getItemCount(): Int = steps.size

    // 외부에서 데이터를 업데이트할 수 있는 함수
    fun updateSteps(newSteps: List<RecipeStep>) {
        steps = newSteps.sortedBy { it.stepNumber } // stepNumber 기준으로 정렬
        notifyDataSetChanged()
    }


    class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stepNumberTextView: TextView = itemView.findViewById(R.id.tv_step_number)
        private val stepDescriptionTextView: TextView = itemView.findViewById(R.id.tv_step_description)
        private val stepImageView: ImageView = itemView.findViewById(R.id.iv_step_image)
        private val stepTimerLayout: LinearLayout = itemView.findViewById(R.id.layout_step_timer)
        private val stepTimeTextView: TextView = itemView.findViewById(R.id.tv_step_time)

        fun bind(step: RecipeStep) {
            stepNumberTextView.text = step.stepNumber?.toString() ?: ""
            stepDescriptionTextView.text = step.description

            if (!step.imageUrl.isNullOrEmpty()) {
                stepImageView.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(step.imageUrl)
                    .placeholder(R.drawable.ic_launcher_background) // 로딩 중 이미지
                    .error(R.drawable.ic_launcher_foreground) // 에러 시 이미지
                    .into(stepImageView)
            } else {
                stepImageView.visibility = View.GONE
            }

            if (step.useTimer == true && step.time != null && step.time > 0) {
                stepTimerLayout.visibility = View.VISIBLE
                stepTimeTextView.text = "${step.time}분"
            } else {
                stepTimerLayout.visibility = View.GONE
            }
        }
    }
}