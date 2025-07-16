package com.example.recipe_pocket.ui.user

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.ActivityTitleListBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class TitleListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTitleListBinding
    private lateinit var titleAdapter: TitleAdapter
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTitleListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        loadTitles()
    }

    private fun setupUI() {
        binding.ivBackButton.setOnClickListener { finish() }

        binding.btnRemoveTitle.setOnClickListener {
            removeUserTitle()
        }
    }

    private fun setupRecyclerView() {
        titleAdapter = TitleAdapter(emptyList(), "") { selectedTitle ->
            // 어댑터에서 '설정하기' 버튼 클릭 시 호출될 람다 함수
            updateUserTitle(selectedTitle)
        }
        binding.recyclerViewTitles.apply {
            layoutManager = LinearLayoutManager(this@TitleListActivity)
            adapter = titleAdapter
        }
    }

    private fun loadTitles() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            finish()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                binding.progressBar.visibility = View.GONE
                if (document.exists()) {
                    val unlockedTitles = document.get("unlockedTitles") as? List<String> ?: emptyList()
                    val currentTitle = document.getString("title") ?: ""

                    if (unlockedTitles.isEmpty()) {
                        binding.tvEmptyList.visibility = View.VISIBLE
                        binding.tvEmptyList.text = "획득한 칭호가 없습니다."
                    } else {
                        binding.tvEmptyList.visibility = View.GONE
                        titleAdapter.updateTitles(unlockedTitles, currentTitle)
                    }
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyList.visibility = View.VISIBLE
                binding.tvEmptyList.text = "칭호를 불러오는데 실패했습니다."
            }
    }

    private fun updateUserTitle(title: String) {
        val currentUser = auth.currentUser ?: return
        firestore.collection("Users").document(currentUser.uid)
            .update("title", title)
            .addOnSuccessListener {
                Toast.makeText(this, "'$title'(으)로 칭호가 변경되었습니다.", Toast.LENGTH_SHORT).show()
                // 변경된 내용을 어댑터에 즉시 반영
                titleAdapter.setCurrentTitle(title)
            }
            .addOnFailureListener {
                Toast.makeText(this, "칭호 변경에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }
    
    //칭호 해제 함수
    private fun removeUserTitle() {
        val currentUser = auth.currentUser ?: return
        // Firestore의 title 필드를 빈 문자열 "" 로 업데이트합니다.
        firestore.collection("Users").document(currentUser.uid)
            .update("title", "")
            .addOnSuccessListener {
                Toast.makeText(this, "칭호가 해제되었습니다.", Toast.LENGTH_SHORT).show()
                // UI에 즉시 반영
                titleAdapter.setCurrentTitle("")
            }
            .addOnFailureListener {
                Toast.makeText(this, "칭호 해제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }
}