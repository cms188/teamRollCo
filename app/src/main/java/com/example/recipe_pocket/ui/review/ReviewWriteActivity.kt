package com.example.recipe_pocket.ui.review

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.Review
import com.example.recipe_pocket.databinding.ActivityReviewWriteBinding
// ▼▼▼ [추가] RecipeDetailActivity를 import 합니다. ▼▼▼
import com.example.recipe_pocket.ui.recipe.read.RecipeDetailActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.util.*

class ReviewWriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewWriteBinding
    private var recipeId: String? = null
    private var reviewId: String? = null
    private var recipe: Recipe? = null
    private var editReview: Review? = null

    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage

    private lateinit var imageAdapter: ReviewImageAdapter
    private val selectedImageUris = mutableListOf<Uri>()

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    selectedImageUris.add(clipData.getItemAt(i).uri)
                }
            } ?: result.data?.data?.let { uri ->
                selectedImageUris.add(uri)
            }
            imageAdapter.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewWriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reviewId = intent.getStringExtra("REVIEW_ID")
        // MyReviewsActivity에서 넘어올 때 recipeId가 없을 수 있으므로, editReview에서 먼저 가져옵니다.
        val serializableReview = intent.getSerializableExtra("EDIT_REVIEW_DATA") as? Review
        editReview = serializableReview

        recipeId = editReview?.recipeId ?: intent.getStringExtra("RECIPE_ID")


        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보 오류", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        loadRecipeInfo()
        setupClickListeners()

        if (editReview != null) {
            populateReviewDataForEdit()
        }
    }

    private fun loadRecipeInfo() {
        firestore.collection("Recipes").document(recipeId!!).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    recipe = document.toObject(Recipe::class.java)
                    binding.title.text = recipe?.title ?: "레시피"
                    Glide.with(this).load(recipe?.thumbnailUrl).into(binding.mainImage)

                    if (reviewId != null && editReview == null) {
                        loadReviewDataForEdit()
                    }
                } else {
                    Toast.makeText(this, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "레시피 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadReviewDataForEdit() {
        firestore.collection("Recipes").document(recipeId!!)
            .collection("Reviews").document(reviewId!!)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    editReview = document.toObject(Review::class.java)?.apply { id = document.id }
                    populateReviewDataForEdit()
                } else {
                    Toast.makeText(this, "수정할 리뷰를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "리뷰 정보를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun populateReviewDataForEdit() {
        binding.toolbar.toolbarTitle.text = "리뷰 수정"
        binding.btnSubmit.text = "수정 완료"

        editReview?.let { review ->
            binding.customRatingBar.rating = review.rating
            binding.editTextFeedback.setText(review.comment)

            when (review.flavor) {
                "맛있어요" -> binding.radioFlavorGood.isChecked = true
                "먹을만해요" -> binding.radioFlavorNormal.isChecked = true
                "별로예요" -> binding.radioFlavorBad.isChecked = true
            }

            when (review.difficulty) {
                "쉬워요" -> binding.radioDifficultyEasy.isChecked = true
                "보통이에요" -> binding.radioDifficultyNormal.isChecked = true
                "어려워요" -> binding.radioDifficultyHard.isChecked = true
            }
            Toast.makeText(this, "기존 이미지는 수정할 수 없습니다. 새 이미지를 추가해주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ReviewImageAdapter(selectedImageUris) { position ->
            selectedImageUris.removeAt(position)
            imageAdapter.notifyDataSetChanged()
        }
        binding.rvReviewImages.apply {
            adapter = imageAdapter
            layoutManager = LinearLayoutManager(this@ReviewWriteActivity, LinearLayoutManager.HORIZONTAL, false)
        }
    }

    private fun setupClickListeners() {
        binding.toolbar.backButton.setOnClickListener { finish() }
        binding.btnAddPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickImagesLauncher.launch(intent)
        }
        binding.btnSubmit.setOnClickListener { submitReview() }
    }

    private fun submitReview() {
        val currentUser = auth.currentUser ?: return

        val rating = binding.customRatingBar.rating
        val comment = binding.editTextFeedback.text.toString()
        val flavor = when (binding.radioFlavor.checkedRadioButtonId) {
            R.id.radio_flavor_good -> "맛있어요"
            R.id.radio_flavor_normal -> "먹을만해요"
            R.id.radio_flavor_bad -> "별로예요"
            else -> null
        }
        val difficulty = when (binding.radioDifficulty.checkedRadioButtonId) {
            R.id.radio_difficulty_easy -> "쉬워요"
            R.id.radio_difficulty_normal -> "보통이에요"
            R.id.radio_difficulty_hard -> "어려워요"
            else -> null
        }

        if (rating == 0f || comment.isBlank() || flavor == null || difficulty == null) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSubmit.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        val uploadTasks = selectedImageUris.map { uri ->
            val ref = storage.reference.child("review_images/${UUID.randomUUID()}")
            ref.putFile(uri).continueWithTask { it.result.storage.downloadUrl }
        }

        Tasks.whenAllSuccess<Uri>(uploadTasks).addOnSuccessListener { uris ->
            val imageUrls = uris.map { it.toString() }

            val currentReviewId = editReview?.id ?: reviewId
            if (currentReviewId != null) {
                val updatedData = mapOf(
                    "rating" to rating,
                    "comment" to comment,
                    "flavor" to flavor,
                    "difficulty" to difficulty,
                    "imageUrls" to (editReview!!.imageUrls.orEmpty() + imageUrls)
                )
                firestore.collection("Recipes").document(recipeId!!).collection("Reviews").document(currentReviewId)
                    .update(updatedData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "리뷰가 수정되었습니다.", Toast.LENGTH_SHORT).show()
                        goToDetailActivity()
                    }.addOnFailureListener { e ->
                        binding.btnSubmit.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "수정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                firestore.collection("Users").document(currentUser.uid).get().addOnSuccessListener { userDoc ->
                    val newReview = Review(
                        recipeId = recipeId,
                        recipeTitle = recipe?.title,
                        userId = currentUser.uid,
                        userNickname = userDoc.getString("nickname"),
                        userProfileUrl = userDoc.getString("profileImageUrl"),
                        rating = rating,
                        flavor = flavor,
                        difficulty = difficulty,
                        comment = comment,
                        imageUrls = imageUrls,
                        createdAt = Timestamp.now()
                    )
                    checkExistingReviewAndSave(newReview)
                }
            }
        }.addOnFailureListener {
            binding.btnSubmit.isEnabled = true
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "이미지 업로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkExistingReviewAndSave(review: Review) {
        val currentUser = auth.currentUser ?: return
        val reviewCollection = firestore.collection("Recipes").document(recipeId!!).collection("Reviews")

        reviewCollection.whereEqualTo("userId", currentUser.uid).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    saveReviewToFirestore(review)
                } else {
                    Toast.makeText(this, "이미 이 레시피에 대한 리뷰를 작성했습니다.", Toast.LENGTH_LONG).show()
                    binding.btnSubmit.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                binding.btnSubmit.isEnabled = true
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "리뷰 확인 중 오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ▼▼▼ [수정] 함수 이름과 내용을 RecipeDetailActivity로 직접 이동하도록 변경 ▼▼▼
    private fun goToDetailActivity() {
        val intent = Intent(this, RecipeDetailActivity::class.java).apply {
            putExtra("RECIPE_ID", recipeId)
            // 새로운 태스크를 만들고 기존 스택을 모두 지워서 Detail 화면만 남도록 함
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish() // 현재 액티비티 종료
    }


    private fun saveReviewToFirestore(review: Review) {
        val reviewRef = firestore.collection("Recipes").document(recipeId!!).collection("Reviews").document()
        firestore.runTransaction { transaction ->
            transaction.set(reviewRef, review)
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "리뷰가 성공적으로 등록되었습니다.", Toast.LENGTH_SHORT).show()
            goToDetailActivity()
        }.addOnFailureListener { e ->
            binding.btnSubmit.isEnabled = true
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "리뷰 등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}