package com.example.recipe_pocket.ui.recipe.write

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.example.recipe_pocket.R
import com.example.recipe_pocket.databinding.ActivityCategorySelectionBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class CategorySelection : BottomSheetDialogFragment() {

    private var _binding: ActivityCategorySelectionBinding? = null
    private val binding get() = _binding!!
    private val selectedCategories = mutableSetOf<String>()

    private val categoryByType get() = resources.getStringArray(R.array.categories_by_type)
    private val categoryByDish get() = resources.getStringArray(R.array.categories_by_dish)
    private val categoryByTheme get() = resources.getStringArray(R.array.categories_by_theme)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityCategorySelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadInitialSelection()
        populateCategoryChips()
        setupSaveButton()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadInitialSelection() {
        arguments?.getStringArrayList(ARG_SELECTED_CATEGORIES)?.let {
            selectedCategories.addAll(it)
        }
    }

    private fun populateCategoryChips() {
        addChipsToGroup(binding.chipGroupType, categoryByType)
        addChipsToGroup(binding.chipGroupDish, categoryByDish)
        addChipsToGroup(binding.chipGroupTheme, categoryByTheme)
    }

    private fun addChipsToGroup(chipGroup: ChipGroup, categories: Array<String>) {
        categories.forEach { category ->
            chipGroup.addView(createCategoryChip(category))
        }
    }

    private fun createCategoryChip(categoryText: String): Chip {
        return Chip(ContextThemeWrapper(requireContext(), R.style.CategorySelectionChip)).apply {
            text = categoryText
            isCheckable = true
            isChecked = selectedCategories.contains(categoryText)

            //선택 시 체크 아이콘 제거
            this.checkedIcon = null

            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCategories.add(categoryText)
                } else {
                    selectedCategories.remove(categoryText)
                }
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveCategories.setOnClickListener {
            returnSelectedCategories()
        }
    }

    private fun returnSelectedCategories() {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(RESULT_SELECTED_CATEGORIES to ArrayList(selectedCategories))
        )
        dismiss()
    }

    companion object {
        const val TAG = "카테고리선택"
        const val REQUEST_KEY = "선택요청"
        const val RESULT_SELECTED_CATEGORIES = "결과"
        private const val ARG_SELECTED_CATEGORIES = "초기값"

        fun newInstance(selectedCategories: List<String>): CategorySelection {
            return CategorySelection().apply {
                arguments = bundleOf(
                    ARG_SELECTED_CATEGORIES to ArrayList(selectedCategories)
                )
            }
        }
    }
}