package com.example.recipe_pocket.ui.tip
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.ActivityMyTipsBinding
import com.example.recipe_pocket.repository.CookingTipLoader
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class MyTipsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyTipsBinding
    private lateinit var listAdapter: CookTipListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyTipsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadMyTips()
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
        binding.recyclerViewMyTips.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(this@MyTipsActivity)
        }
    }

    private fun loadMyTips() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            binding.tvEmptyList.text = "로그인이 필요합니다."
            binding.tvEmptyList.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = CookingTipLoader.loadTipsByUserId(currentUser.uid)
            binding.progressBar.visibility = View.GONE

            result.onSuccess { tips ->
                if (tips.isEmpty()) {
                    binding.tvEmptyList.visibility = View.VISIBLE
                    binding.tvEmptyList.text = "작성한 요리 팁이 없습니다."
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