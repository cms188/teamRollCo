package com.example.recipe_pocket.ui.recipe.write

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.ActivityTempSaveBinding
import com.example.recipe_pocket.repository.RecipeSavePipeline
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class TempSaveListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTempSaveBinding
    private lateinit var listAdapter: TempSaveListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTempSaveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        utils.ToolbarUtils.setupTransparentToolbar(this, "임시저장 목록") { finish() }
        setupRecyclerView()
        loadTempSaves()
    }

    override fun onResume() {
        super.onResume()
        loadTempSaves()
    }

    private fun setupRecyclerView() {
        listAdapter = TempSaveListAdapter(emptyList()) { draft -> onDraftSelected(draft) }
        binding.recyclerViewLikedTips.apply {
            adapter = listAdapter
            layoutManager = LinearLayoutManager(this@TempSaveListActivity)
        }
    }

    private fun loadTempSaves() {
        if (Firebase.auth.currentUser == null) {
            binding.tvEmptyList.text = "로그인이 필요합니다."
            binding.tvEmptyList.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyList.visibility = View.GONE

        lifecycleScope.launch {
            val result = runCatching { RecipeSavePipeline.fetchTempSaves() }
            binding.progressBar.visibility = View.GONE

            result.onSuccess { drafts ->
                listAdapter.updateDrafts(drafts)
                if (drafts.isEmpty()) {
                    binding.tvEmptyList.visibility = View.VISIBLE
                    binding.tvEmptyList.text = "임시 저장된 레시피가 없습니다."
                }
            }.onFailure {
                binding.tvEmptyList.visibility = View.VISIBLE
                binding.tvEmptyList.text = "임시저장을 불러오지 못했습니다."
            }
        }
    }

    private fun onDraftSelected(draft: com.example.recipe_pocket.data.TempSaveDraft) {
        if (Firebase.auth.currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val deleteResult = runCatching { RecipeSavePipeline.deleteTempSave(draft.id) }
            binding.progressBar.visibility = View.GONE
            if (deleteResult.isFailure) {
                Toast.makeText(this@TempSaveListActivity, "임시저장 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("recipe_data", draft.recipe)
            })
            finish()
        }
    }
}
