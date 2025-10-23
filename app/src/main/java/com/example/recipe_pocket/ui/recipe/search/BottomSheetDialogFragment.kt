package com.example.recipe_pocket.ui.recipe.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.example.recipe_pocket.CategoryPageActivity
import com.example.recipe_pocket.databinding.DialogSearchFilterBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: DialogSearchFilterBinding? = null
    private val binding get() = _binding!!

    private var listener: OnFilterAppliedListener? = null
    private lateinit var currentFilter: SearchFilter

    interface OnFilterAppliedListener {
        fun onFilterApplied(filter: SearchFilter)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = parentFragment as? OnFilterAppliedListener ?: context as? OnFilterAppliedListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSearchFilterBinding.inflate(inflater, container, false)
        currentFilter = arguments?.getParcelable(ARG_FILTER) ?: SearchFilter.default()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (requireActivity() is CategoryPageActivity) {
            binding.FilterCategory.visibility = View.GONE
            binding.chipgroupCategoryOptions.clearCheck()
        }

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        // 1. 작성 기간 (단일 선택)
        val periodIndex = when (currentFilter.creationPeriod) {
            CreationPeriod.TODAY -> 0
            CreationPeriod.WEEK -> 1
            CreationPeriod.MONTH -> 2
            CreationPeriod.YEAR -> 3
            CreationPeriod.ALL -> 4
        }
        (binding.chipgroupPeriod.getChildAt(periodIndex) as? Chip)?.isChecked = true
        setupSingleSelectBehavior(binding.chipgroupPeriod)

        // 2. 카테고리 (다중 선택)
        setupMultiSelectBehavior(binding.chipgroupCategoryOptions, binding.chipCategoryAll, currentFilter.categories)

        // 3. 난이도 (다중 선택)
        setupMultiSelectBehavior(binding.chipgroupDifficultyOptions, binding.chipDifficultyAll, currentFilter.difficulty)

        // 4. 조리 시간 (단일 선택)
        val timeIndex = when (currentFilter.cookingTime) {
            CookingTime.UNDER_10 -> 0
            CookingTime.UNDER_20 -> 1
            CookingTime.UNDER_30 -> 2
            CookingTime.ALL -> 3
        }
        (binding.chipgroupTime.getChildAt(timeIndex) as? Chip)?.isChecked = true
        setupSingleSelectBehavior(binding.chipgroupTime)
    }

    // 단일 선택 ChipGroup 동작 설정
    private fun setupSingleSelectBehavior(chipGroup: ChipGroup) {
        chipGroup.children.forEach { chipView ->
            (chipView as Chip).setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked) {
                    // 선택된 칩 외에는 모두 선택 해제
                    chipGroup.children.filter { it.id != buttonView.id }.forEach {
                        (it as Chip).isChecked = false
                    }
                }
            }
        }
    }

    // 다중 선택 ChipGroup 동작 설정
    private fun setupMultiSelectBehavior(optionsGroup: ChipGroup, allChip: Chip, selectedItems: Set<String>) {
        val optionChips = optionsGroup.children.filter { it.id != allChip.id }.toList()

        // 초기 상태 설정
        if (selectedItems.isEmpty()) {
            allChip.isChecked = true
        } else {
            allChip.isChecked = false // 옵션이 선택되어 있으면 '전체'는 해제
            optionChips.forEach {
                val chip = it as Chip
                chip.isChecked = selectedItems.contains(chip.text.toString())
            }
        }

        // '전체' 칩 리스너
        allChip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // '전체'가 선택되면 다른 옵션들 해제
                optionChips.forEach { (it as Chip).isChecked = false }
            }
        }

        // 개별 옵션 칩 리스너
        optionChips.forEach {
            (it as Chip).setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // 옵션이 선택되면 '전체'는 해제
                    allChip.isChecked = false
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnReset.setOnClickListener { resetFilters() }
        binding.btnApply.setOnClickListener { applyFilters() }
    }

    private fun resetFilters() {
        // 단일 선택 그룹 리셋 ('전체' 칩을 선택)
        (binding.chipgroupPeriod.getChildAt(4) as Chip).isChecked = true
        (binding.chipgroupTime.getChildAt(3) as Chip).isChecked = true

        // 다중 선택 그룹 리셋 ('전체' 칩을 선택)
        binding.chipCategoryAll.isChecked = true
        binding.chipDifficultyAll.isChecked = true
    }

    private fun applyFilters() {
        val period = when (findCheckedChipIndex(binding.chipgroupPeriod)) {
            0 -> CreationPeriod.TODAY
            1 -> CreationPeriod.WEEK
            2 -> CreationPeriod.MONTH
            3 -> CreationPeriod.YEAR
            else -> CreationPeriod.ALL
        }

        val categories = if (binding.chipCategoryAll.isChecked) emptySet() else
            binding.chipgroupCategoryOptions.children
                .filter { (it as Chip).isChecked && it.id != binding.chipCategoryAll.id }
                .map { (it as Chip).text.toString() }
                .toSet()

        val difficulties = if (binding.chipDifficultyAll.isChecked) emptySet() else
            binding.chipgroupDifficultyOptions.children
                .filter { (it as Chip).isChecked && it.id != binding.chipDifficultyAll.id }
                .map { (it as Chip).text.toString() }
                .toSet()

        val time = when (findCheckedChipIndex(binding.chipgroupTime)) {
            0 -> CookingTime.UNDER_10
            1 -> CookingTime.UNDER_20
            2 -> CookingTime.UNDER_30
            else -> CookingTime.ALL
        }

        listener?.onFilterApplied(SearchFilter(period, difficulties, time, categories))
        dismiss()
    }

    private fun findCheckedChipIndex(chipGroup: ChipGroup): Int {
        return chipGroup.children.indexOfFirst { (it as Chip).isChecked }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        // 바텀시트의 최대 높이를 설정하여 버튼이 가려지지 않도록 함
        val dialog = dialog as? BottomSheetDialog
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                val layoutParams = sheet.layoutParams

                // 화면 높이의 85% 정도로 최대 높이 설정
                val windowHeight = resources.displayMetrics.heightPixels
                layoutParams.height = (windowHeight * 0.85).toInt()
                sheet.layoutParams = layoutParams

                behavior.peekHeight = layoutParams.height
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    companion object {
        private const val ARG_FILTER = "current_filter"
        fun newInstance(filter: SearchFilter): FilterBottomSheetFragment {
            return FilterBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FILTER, filter)
                }
            }
        }
    }
}