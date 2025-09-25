package com.example.recipe_pocket.ui.recipe.write

import android.os.Bundle
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

class CategorySelection : BottomSheetDialogFragment() {

    private var _binding: ActivityCategorySelectionBinding? = null
    private val binding get() = _binding!!
    private val selectedCategories = mutableSetOf<String>()

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
        val categories = resources.getStringArray(R.array.recipe_categories)
        categories.forEach { category ->
            binding.chipGroupCategories.addView(createCategoryChip(category))
        }
    }

    private fun createCategoryChip(categoryText: String): Chip {
        return Chip(requireContext()).apply {
            text = categoryText
            isCheckable = true
            isChecked = selectedCategories.contains(categoryText)
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
