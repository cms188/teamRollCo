package com.example.recipe_pocket

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class RecipeStepAdapter(private var steps: List<RecipeStep>) :
    RecyclerView.Adapter<RecipeStepAdapter.StepViewHolder>() {

    // ViewHolder 인스턴스를 추적하기 위한 세트
    private val viewHolders = mutableSetOf<StepViewHolder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.read_recipe_step, parent, false)
        val holder = StepViewHolder(view)
        viewHolders.add(holder) // 생성 시 세트에 추가
        return holder
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = steps[position]
        holder.bind(step)
    }

    override fun getItemCount(): Int = steps.size

    override fun onViewRecycled(holder: StepViewHolder) {
        super.onViewRecycled(holder)
        // 뷰가 재사용될 때 타이머를 초기화하고 세트에서 제거
        holder.releaseCircularTimer()
        viewHolders.remove(holder)
    }
    @SuppressLint("NotifyDataSetChanged")
    fun updateSteps(newSteps: List<RecipeStep>) {
        steps = newSteps.sortedBy { it.stepNumber }
        notifyDataSetChanged()
    }

    // Activity에서 호출하여 모든 타이머를 정리하는 메서드
    fun releaseAllTimers() {
        viewHolders.forEach { it.releaseCircularTimer() }
        viewHolders.clear()
    }

    class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 뷰 참조에서 stepTimerLayout과 stepTimeTextView 제거
        private val stepTitleTextView: TextView = itemView.findViewById(R.id.tv_step_title)
        private val stepDescriptionTextView: TextView = itemView.findViewById(R.id.tv_step_description)
        private val stepImageView: ImageView = itemView.findViewById(R.id.iv_step_image)
        private val circularTimerView: CircularTimerView = itemView.findViewById(R.id.circular_timer_view)

        fun bind(step: RecipeStep) {
            // 제목만 표시 (단계 번호 없음)
            stepTitleTextView.text = if (step.title.isNullOrEmpty()) "요리하기" else step.title

            // 설명 표시
            stepDescriptionTextView.text = step.description

            // 이미지 표시 (if-else 구조 유지)
            if (!step.imageUrl.isNullOrEmpty()) {
                stepImageView.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(step.imageUrl)
                    .placeholder(R.drawable.bg_bookmark_shape)
                    .error(R.drawable.bg_bookmark_shape)
                    .into(stepImageView)
            } else {
                stepImageView.visibility = View.GONE
            }

            // CircularTimerView 상태 관리 (if-else 구조 유지)
            if (step.useTimer == true && step.time != null && step.time > 0) {
                circularTimerView.visibility = View.VISIBLE
                circularTimerView.setTime(step.time)
            } else {
                circularTimerView.visibility = View.GONE
            }
        }

        // 리소스 정리 메서드 (그대로 유지)
        fun releaseCircularTimer() {
            circularTimerView.releaseTimer()
        }
    }
}