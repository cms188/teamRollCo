package com.example.recipe_pocket

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class CookWrite03StepAdapter(
    fragmentActivity: FragmentActivity,
    private val steps: List<RecipeStep>
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = steps.size

    override fun createFragment(position: Int): Fragment {
        return CookWrite03StepFragment.newInstance(steps[position])
    }
}