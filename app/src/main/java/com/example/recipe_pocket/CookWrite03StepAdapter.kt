package com.example.recipe_pocket

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter

// FragmentStateAdapter를 상속받는 것은 유지
class CookWrite03StepAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    // 어댑터가 현재 리스트를 직접 관리
    private var steps: List<RecipeStep_write> = emptyList()

    // DiffUtil을 사용하여 리스트를 업데이트하는 함수
    fun submitList(newSteps: List<RecipeStep_write>) {
        val diffCallback = StepDiffCallback(this.steps, newSteps)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.steps = newSteps
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = steps.size

    override fun createFragment(position: Int): Fragment {
        return CookWrite03StepFragment.newInstance(position)
    }

    override fun getItemId(position: Int): Long = steps[position].id.hashCode().toLong()
    override fun containsItem(itemId: Long): Boolean = steps.any { it.id.hashCode().toLong() == itemId }

    // DiffUtil.Callback 구현
    private class StepDiffCallback(
        private val oldList: List<RecipeStep_write>,
        private val newList: List<RecipeStep_write>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}