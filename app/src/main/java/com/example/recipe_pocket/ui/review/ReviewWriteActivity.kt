package com.example.recipe_pocket.ui.review

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.NotificationType
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.Review
import com.example.recipe_pocket.databinding.ActivityReviewWriteBinding
import com.example.recipe_pocket.repository.NotificationHandler
import com.example.recipe_pocket.ui.recipe.read.RecipeDetailActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import utils.ToolbarUtils
import java.util.*

class ReviewWriteActivity : AppCompatActivity() {
    private companion object {
        const val TOOLBAR_HEIGHT_DP = 56                // 툴바 기본 높이
        const val IMAGE_OVERLAP_RATIO = 0.4f            // 이미지와 바텀시트 겹침 비율
        const val MIN_PEEK_HEIGHT_DP = 300              // 바텀시트 최소 peek 높이
        const val TRANSPARENCY_START_THRESHOLD = 0.7f   // 투명도 변경 시작 지점 (%)
        const val ANIMATION_DURATION = 150L             // 애니메이션 지속 시간 (ms)
    }

    // 뷰 선언
    private lateinit var toolbar: Toolbar
    private lateinit var mainImage: ImageView
    private lateinit var title: TextView
    private lateinit var bottomSheet: NestedScrollView
    private lateinit var customRatingBar: AppCompatRatingBar
    private lateinit var radioFlavorGroup: RadioGroup
    private lateinit var radioFlavorGood: RadioButton
    private lateinit var radioFlavorNormal: RadioButton
    private lateinit var radioFlavorBad: RadioButton
    private lateinit var radioDifficultyGroup: RadioGroup
    private lateinit var radioDifficultyEasy: RadioButton
    private lateinit var radioDifficultyNormal: RadioButton
    private lateinit var radioDifficultyHard: RadioButton
    private lateinit var editTextFeedback: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSubmit: MaterialButton

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<NestedScrollView>

    // 뷰 초기화
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        mainImage = findViewById(R.id.main_image)
        title = findViewById(R.id.title)
        bottomSheet = findViewById(R.id.bottom_sheet)
        customRatingBar = findViewById(R.id.customRatingBar)

        // 맛 평가 라디오 그룹
        radioFlavorGroup = findViewById(R.id.radio_flavor)
        radioFlavorGood = findViewById(R.id.radio_flavor_good)
        radioFlavorNormal = findViewById(R.id.radio_flavor_normal)
        radioFlavorBad = findViewById(R.id.radio_flavor_bad)

        // 난이도 평가 라디오 그룹
        radioDifficultyGroup = findViewById(R.id.radio_difficulty)
        radioDifficultyEasy = findViewById(R.id.radio_difficulty_easy)
        radioDifficultyNormal = findViewById(R.id.radio_difficulty_normal)
        radioDifficultyHard = findViewById(R.id.radio_difficulty_hard)

        editTextFeedback = findViewById(R.id.editText_feedback)
        progressBar = findViewById(R.id.progress_bar)
        btnSubmit = findViewById(R.id.btn_submit)

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
    }

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

        ToolbarUtils.setupTransparentToolbar(this, "")
        initViews()
        setupUI()
        setupListeners()
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
                "보통이에요" -> binding.radioFlavorNormal.isChecked = true
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
            R.id.radio_flavor_normal -> "보통이에요"
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

            lifecycleScope.launch {
                recipe?.userId?.let { recipientId ->
                    auth.currentUser?.uid?.let { senderId ->
                        // addLikeReviewNotification 함수 호출
                        NotificationHandler.addLikeReviewNotification(
                            recipientId = recipientId,
                            senderId = senderId,
                            recipe = recipe!!,
                            type = NotificationType.REVIEW
                        )
                    }
                }
            }

            goToDetailActivity()
        }.addOnFailureListener { e ->
            binding.btnSubmit.isEnabled = true
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "리뷰 등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    // UI 초기화 및 설정
    private fun setupUI() {
        // 바텀시트 위치 설정 (이미지 로드 후)
        setupBottomSheet()

        // 바텀시트 콜백 설정 (이미지 투명도 변경)
        setupBottomSheetCallbacks()
    }

    // 리스너 설정
    private fun setupListeners() {
        // 별점 변경 리스너
        customRatingBar.setOnRatingBarChangeListener { _, rating, _ ->
            // 별점이 변경될 때마다 호출됨
            updateSubmitButtonState()
        }

        // 맛 평가 라디오 그룹 리스너
        radioFlavorGroup.setOnCheckedChangeListener { _, _ ->
            updateSubmitButtonState()
        }

        // 난이도 평가 라디오 그룹 리스너
        radioDifficultyGroup.setOnCheckedChangeListener { _, _ ->
            updateSubmitButtonState()
        }

        // 제출 버튼 클릭 리스너
        btnSubmit.setOnClickListener {
            submitReview()
        }
    }

    // 바텀시트 위치와 동작 설정
    private fun setupBottomSheet() {
        mainImage.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mainImage.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // 화면 및 뷰 크기 정보 수집
                val screenHeight = resources.displayMetrics.heightPixels
                val toolbarHeight = toolbar.height
                val imageHeight = mainImage.height

                // 바텀시트 peek height 계산
                val peekHeight = calculatePeekHeight(screenHeight, toolbarHeight, imageHeight)

                // 바텀시트 동작 설정
                configureBehavior(peekHeight, toolbarHeight)

                // 바텀시트 최대 높이 제한
                limitMaxHeight(screenHeight, toolbarHeight)
            }
        })
    }

    // 바텀시트 peek height 계산
    private fun calculatePeekHeight(screenHeight: Int, toolbarHeight: Int, imageHeight: Int): Int {
        val imageBottomPosition = toolbarHeight + imageHeight
        val overlapOffset = (imageHeight * IMAGE_OVERLAP_RATIO).toInt()
        val startPosition = imageBottomPosition - overlapOffset
        val calculatedHeight = screenHeight - startPosition
        return calculatedHeight.coerceAtLeast(dpToPx(MIN_PEEK_HEIGHT_DP))
    }

    // 바텀시트 동작 설정
    private fun configureBehavior(peekHeight: Int, toolbarHeight: Int) {
        bottomSheetBehavior.apply {
            this.peekHeight = peekHeight
            expandedOffset = toolbarHeight
            isHideable = false
        }
    }

    // 바텀시트 높이 제한
    private fun limitMaxHeight(screenHeight: Int, toolbarHeight: Int) {
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = screenHeight - toolbarHeight
        }
    }

    // 바텀시트 스크롤 시 이미지 투명도 변경
    private fun setupBottomSheetCallbacks() {
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // 상태 변경 시 필요한 처리
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset >= TRANSPARENCY_START_THRESHOLD) {
                    val adjustedOffset =
                        (slideOffset - TRANSPARENCY_START_THRESHOLD) / (1.0f - TRANSPARENCY_START_THRESHOLD)
                    mainImage.alpha = 1.0f - adjustedOffset
                } else {
                    mainImage.alpha = 1.0f
                }
            }
        })
    }

    // 제출 버튼 활성화 상태 업데이트
    private fun updateSubmitButtonState() {
        val hasRating = customRatingBar.rating > 0
        val hasFlavor = radioFlavorGroup.checkedRadioButtonId != -1
        val hasDifficulty = radioDifficultyGroup.checkedRadioButtonId != -1

        // 모든 필수 항목이 선택되었는지 확인
        val isValid = hasRating && hasFlavor && hasDifficulty

        btnSubmit.isEnabled = isValid
        btnSubmit.alpha = if (isValid) 1.0f else 0.5f
    }

    // dp를 px로 변환
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

}