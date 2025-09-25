package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.recipe_pocket.R
import com.example.recipe_pocket.databinding.ActivityCategorySelectionBinding
import com.google.android.material.chip.Chip

class CategorySelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategorySelectionBinding
    private val selectedCategories = mutableSetOf<String>()

    companion object {
        const val EXTRA_SELECTED_CATEGORIES = "EXTRA_SELECTED_CATEGORIES"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategorySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        utils.ToolbarUtils.setupTransparentToolbar(this, "")
        loadInitialSelection()
        populateCategoryChips()
        setupSaveButton()
    }

    private fun loadInitialSelection() {
        intent.getStringArrayListExtra(EXTRA_SELECTED_CATEGORIES)?.let {
            selectedCategories.addAll(it)
        }
    }

    private fun populateCategoryChips() {
        val categories = resources.getStringArray(R.array.recipe_categories)
        for (category in categories) {
            val chip = createCategoryChip(category)
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun createCategoryChip(categoryText: String): Chip {
        return Chip(this).apply {
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
        val resultIntent = Intent().apply {
            putStringArrayListExtra(EXTRA_SELECTED_CATEGORIES, ArrayList(selectedCategories))
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}