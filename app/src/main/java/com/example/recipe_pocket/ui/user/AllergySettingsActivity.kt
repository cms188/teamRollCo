package com.example.recipe_pocket.ui.user

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.ActivityAllergySettingsBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import utils.ToolbarUtils

class AllergySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllergySettingsBinding
    private lateinit var allergyAdapter: AllergyKeywordAdapter
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private var currentUserUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllergySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserUid = auth.currentUser?.uid
        if (currentUserUid == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ToolbarUtils.setupTransparentToolbar(this, "알레르기 설정")
        setupRecyclerView()
        setupClickListeners()
        loadUserAllergies()
    }

    private fun setupRecyclerView() {
        allergyAdapter = AllergyKeywordAdapter(mutableListOf()) { keyword ->
            // 삭제 버튼 클릭 시 호출될 람다
            removeAllergy(keyword)
        }
        binding.recyclerViewAllergies.apply {
            layoutManager = LinearLayoutManager(this@AllergySettingsActivity)
            adapter = allergyAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnAddAllergy.setOnClickListener {
            val keyword = binding.etAllergyInput.text.toString().trim()
            if (keyword.isNotEmpty()) {
                addAllergy(keyword)
            } else {
                Toast.makeText(this, "키워드를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserAllergies() {
        binding.progressBar.visibility = View.VISIBLE
        firestore.collection("Users").document(currentUserUid!!)
            .get()
            .addOnSuccessListener { document ->
                binding.progressBar.visibility = View.GONE
                if (document != null && document.exists()) {
                    val keywords = document.get("allergyKeywords") as? List<String> ?: emptyList()
                    allergyAdapter.updateKeywords(keywords)
                    updateEmptyViewVisibility(keywords.isEmpty())
                } else {
                    updateEmptyViewVisibility(true)
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "알레르기 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addAllergy(keyword: String) {
        binding.btnAddAllergy.isEnabled = false
        firestore.collection("Users").document(currentUserUid!!)
            .update("allergyKeywords", FieldValue.arrayUnion(keyword))
            .addOnSuccessListener {
                Toast.makeText(this, "'$keyword'가 추가되었습니다.", Toast.LENGTH_SHORT).show()
                binding.etAllergyInput.text?.clear()
                loadUserAllergies() // 목록 새로고침
                binding.btnAddAllergy.isEnabled = true
            }
            .addOnFailureListener {
                Toast.makeText(this, "추가에 실패했습니다.", Toast.LENGTH_SHORT).show()
                binding.btnAddAllergy.isEnabled = true
            }
    }

    private fun removeAllergy(keyword: String) {
        firestore.collection("Users").document(currentUserUid!!)
            .update("allergyKeywords", FieldValue.arrayRemove(keyword))
            .addOnSuccessListener {
                Toast.makeText(this, "'$keyword'가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                loadUserAllergies() // 목록 새로고침
            }
            .addOnFailureListener {
                Toast.makeText(this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEmptyViewVisibility(isEmpty: Boolean) {
        if (isEmpty) {
            binding.tvEmptyList.visibility = View.VISIBLE
            binding.recyclerViewAllergies.visibility = View.GONE
        } else {
            binding.tvEmptyList.visibility = View.GONE
            binding.recyclerViewAllergies.visibility = View.VISIBLE
        }
    }
}