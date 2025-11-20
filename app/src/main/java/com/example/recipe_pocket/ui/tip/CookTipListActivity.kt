package com.example.recipe_pocket.ui.tip

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.ActivityCookTipListBinding
import com.example.recipe_pocket.repository.CookingTipLoader
import kotlinx.coroutines.launch

class CookTipListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookTipListBinding
    private lateinit var listAdapter: CookTipListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookTipListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadTips()
        setupWindowInsets()
        utils.ToolbarUtils.setupTransparentToolbar(this, "요리 Tip")
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupToolbar() {
        //binding.ivBackButton.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        listAdapter = CookTipListAdapter(emptyList()) { tip ->
            // 요리 팁 상세 화면으로 이동하는 인텐트 구현
            val intent = Intent(this, CookTipDetailActivity::class.java).apply {
                putExtra(CookTipDetailActivity.EXTRA_TIP_ID, tip.id)
            }
            startActivity(intent)
        }
        binding.recyclerViewTips.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(this@CookTipListActivity)
        }
    }

    private fun loadTips() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = CookingTipLoader.loadAllTips()
            binding.progressBar.visibility = View.GONE

            result.onSuccess { tips ->
                if (tips.isEmpty()) {
                    binding.tvEmptyList.visibility = View.VISIBLE
                } else {
                    listAdapter.updateTips(tips)
                }
            }.onFailure {
                binding.tvEmptyList.text = "오류가 발생했습니다."
                binding.tvEmptyList.visibility = View.VISIBLE
            }
        }
    }
}