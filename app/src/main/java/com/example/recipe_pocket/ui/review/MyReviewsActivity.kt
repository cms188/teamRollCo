package com.example.recipe_pocket.ui.review

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.data.Review
import com.example.recipe_pocket.databinding.ActivityMyReviewsBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MyReviewsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyReviewsBinding
    private lateinit var reviewAdapter: ReviewAdapter
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyReviewsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        utils.ToolbarUtils.setupTransparentToolbar(this, "작성한 리뷰")
    }

    override fun onResume() {
        super.onResume()
        loadMyReviews()
    }

    private fun setupRecyclerView() {
        reviewAdapter = ReviewAdapter(
            reviews = emptyList(),
            showRecipeTitle = true,
            onItemClick = { review ->
                showEditOrDeleteDialog(review)
            },
            onItemLongClick = null
        )
        binding.recyclerViewMyReviews.apply {
            adapter = reviewAdapter
            layoutManager = LinearLayoutManager(this@MyReviewsActivity)
        }
    }

    private fun showEditOrDeleteDialog(review: Review) {
        val options = arrayOf("수정하기", "삭제하기")
        AlertDialog.Builder(this)
            .setTitle("리뷰 관리")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // 수정
                        val intent = Intent(this, ReviewWriteActivity::class.java).apply {
                            putExtra("REVIEW_ID", review.id)
                            putExtra("RECIPE_ID", review.recipeId)
                        }
                        startActivity(intent)
                    }
                    1 -> { // 삭제
                        showDeleteConfirmationDialog(review)
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun loadMyReviews() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            binding.tvNoReviews.text = "로그인이 필요합니다."
            binding.tvNoReviews.visibility = View.VISIBLE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewMyReviews.visibility = View.GONE
        binding.tvNoReviews.visibility = View.GONE

        firestore.collectionGroup("Reviews")
            .whereEqualTo("userId", currentUser.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                binding.progressBar.visibility = View.GONE
                if (querySnapshot.isEmpty) {
                    binding.tvNoReviews.text = "작성한 리뷰가 없습니다."
                    binding.tvNoReviews.visibility = View.VISIBLE
                } else {
                    val reviews = querySnapshot.toObjects(Review::class.java)
                    reviewAdapter.updateReviews(reviews)
                    binding.recyclerViewMyReviews.visibility = View.VISIBLE
                }
            }.addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvNoReviews.text = "리뷰를 불러오는 중 오류가 발생했습니다."
                binding.tvNoReviews.visibility = View.VISIBLE
                Log.e("MyReviewsActivity", "Error loading reviews", e)
            }
    }

    private fun showDeleteConfirmationDialog(review: Review) {
        AlertDialog.Builder(this)
            .setTitle("리뷰 삭제")
            .setMessage("이 리뷰를 정말로 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteReview(review)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteReview(review: Review) {
        val reviewId = review.id
        val recipeId = review.recipeId

        if (reviewId == null || recipeId == null) {
            Toast.makeText(this, "삭제할 리뷰 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("Recipes").document(recipeId)
            .collection("Reviews").document(reviewId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "리뷰가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                loadMyReviews()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MyReviewsActivity", "Error deleting review", e)
            }
    }
}