package com.example.recipe_pocket.ui.recipe.read

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.R
import com.example.recipe_pocket.databinding.ActivityAllStepsBinding

class AllStepsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllStepsBinding
    private lateinit var stepsAdapter: AllStepsAdapter

    companion object {
        const val EXTRA_STEP_TITLES = "EXTRA_STEP_TITLES"
        const val RESULT_SELECTED_STEP_INDEX = "RESULT_SELECTED_STEP_INDEX"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllStepsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val stepTitles = intent.getStringArrayListExtra(EXTRA_STEP_TITLES) ?: arrayListOf()

        setupToolbar()
        setupRecyclerView(stepTitles)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        binding.toolbar.toolbarTitle.text = "전체 단계 보기"
        binding.toolbar.backButton.setOnClickListener {
            finish()
        }

        val editDeleteContainer = findViewById<View>(R.id.edit_delete_container)
        editDeleteContainer.visibility = View.GONE

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = statusBarHeight + (56 * resources.displayMetrics.density).toInt()
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupRecyclerView(steps: List<String>) {
        stepsAdapter = AllStepsAdapter(steps) { position ->
            val resultIntent = Intent().apply {
                putExtra(RESULT_SELECTED_STEP_INDEX, position)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
        binding.recyclerViewAllSteps.apply {
            layoutManager = LinearLayoutManager(this@AllStepsActivity)
            adapter = stepsAdapter
        }
    }
}