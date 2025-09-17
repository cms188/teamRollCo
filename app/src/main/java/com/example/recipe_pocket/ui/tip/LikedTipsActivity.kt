package com.example.recipe_pocket.ui.tip

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.ActivityLikedTipsBinding
import com.example.recipe_pocket.repository.CookingTipLoader
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class LikedTipsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLikedTipsBinding
    private lateinit var listAdapter: CookTipListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLikedTipsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadLikedTips()
    }

    private fun setupToolbar() {
        binding.ivBackButton.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        listAdapter = CookTipListAdapter(emptyList()) { tip ->
            val intent = Intent(this, CookTipDetailActivity::class.java).apply {
                putExtra(CookTipDetailActivity.EXTRA_TIP_ID, tip.id)
            }
            startActivity(intent)
        }
        binding.recyclerViewLikedTips.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(this@LikedTipsActivity)
        }
    }

    private fun loadLikedTips() {
        if (Firebase.auth.currentUser == null) {
            binding.tvEmptyList.text = "로그인이 필요합니다."
            binding.tvEmptyList.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = CookingTipLoader.loadLikedTips()
            binding.progressBar.visibility = View.GONE

            result.onSuccess { tips ->
                if (tips.isEmpty()) {
                    binding.tvEmptyList.visibility = View.VISIBLE
                    binding.tvEmptyList.text = "추천한 요리 팁이 없습니다."
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