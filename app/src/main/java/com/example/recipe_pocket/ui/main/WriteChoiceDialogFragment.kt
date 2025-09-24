package com.example.recipe_pocket.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.recipe_pocket.databinding.DialogWriteChoiceBinding
import com.example.recipe_pocket.ui.recipe.write.CookWrite01Activity
import com.example.recipe_pocket.ui.tip.CookTipWriteActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class WriteChoiceDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogWriteChoiceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogWriteChoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutWriteRecipe.setOnClickListener {
            startActivity(Intent(requireContext(), CookWrite01Activity::class.java))
            dismiss()
        }

        binding.layoutWriteTip.setOnClickListener {
            startActivity(Intent(requireContext(), CookTipWriteActivity::class.java))
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "WriteChoiceDialog"
    }
}