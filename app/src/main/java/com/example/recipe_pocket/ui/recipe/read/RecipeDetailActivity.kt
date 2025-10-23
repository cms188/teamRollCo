package com.example.recipe_pocket.ui.recipe.read

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.NotificationType
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.Review
import com.example.recipe_pocket.databinding.ActivityRecipeDetailBinding
import com.example.recipe_pocket.databinding.ContentRecipeSummaryBinding
import com.example.recipe_pocket.repository.NotificationHandler
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.ui.review.ReviewAdapter
import com.example.recipe_pocket.ui.recipe.write.RecipeEditActivity
import com.example.recipe_pocket.ui.review.ReviewWriteActivity
import com.example.recipe_pocket.ui.user.RecentlyViewedManager
import com.example.recipe_pocket.ui.user.UserFeedActivity
import com.example.recipe_pocket.ui.user.bookmark.BookmarkManager
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeDetailBinding
    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    private var recipeId: String? = null
    private var currentRecipe: Recipe? = null
    private var currentTab = 0
    private val firestore = Firebase.firestore
    private val auth = Firebase.auth

    private var isTabSelectionInProgress = false
    private val isLayoutReady = AtomicBoolean(false)

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // onResume에서 처리
            }
        }

    private companion object {
        const val TOOLBAR_HEIGHT_DP = 56
        const val IMAGE_OVERLAP_RATIO = 0.4f
        const val MIN_PEEK_HEIGHT_DP = 625 //초기 바텀시트 위치 초기값 400
        const val TRANSPARENCY_START_THRESHOLD = 0.7f
        const val ANIMATION_DURATION = 200L
        const val TAG = "RecipeDetailActivity_DEBUG" // 로그 태그 추가
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra("RECIPE_ID")
        if (recipeId == null) {
            finish()
            return
        }

        initViews()
        setupUI()
        CookingBottomMargin()

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }

    private fun CookingBottomMargin() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnStartCooking) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = dpToPx(16) + navBottom   // 기본 16dp + 네비게이션바 높이
            }
            insets // 소비하지 말고 전파
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun initViews() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        reviewAdapter = ReviewAdapter(emptyList(), showRecipeTitle = false)
    }

    private fun setupUI() {
        // 기본 탭 설정
        binding.btnPageSummary.isChecked = true
        currentTab = 0

        // 툴바 초기 설정
        utils.ToolbarUtils.setupTransparentToolbar(
            this, "", showEditButton = true, showDeleteButton = true,
            onEditClicked = {
                // 수정 버튼 클릭 시 처리
                val intent =
                    Intent(this, RecipeEditActivity::class.java).putExtra("RECIPE_ID", recipeId)
                resultLauncher.launch(intent)
            },
            onDeleteClicked = {
                // 삭제 버튼 클릭 시 처리
                showDeleteConfirmationDialog()
            }
        )

        // 리사이클러뷰 설정
        binding.recyclerViewReviews.apply {
            adapter = reviewAdapter
            layoutManager = LinearLayoutManager(this@RecipeDetailActivity)
            isNestedScrollingEnabled = false
        }

        // 스크롤 리스너 설정 - 스티키 탭 표시/숨김
        binding.nestedScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val toolbarHeight = binding.toolbar.root.height
            val triggerPoint = binding.originalTabContainer.bottom - toolbarHeight

            if (scrollY >= triggerPoint) {
                if (binding.stickyTabContainer.visibility != View.VISIBLE) {
                    binding.stickyTabContainer.visibility = View.VISIBLE
                    binding.originalTabContainer.visibility = View.INVISIBLE
                    binding.toolbar.root.setBackgroundColor(
                        ContextCompat.getColor(
                            this,
                            R.color.white
                        )
                    )
                    binding.stickyTabContainer.bringToFront()

                    // 그라데이션 View 표시 (스티키 탭 바로 위)
                    binding.root.findViewById<View>(R.id.gradient_sticky_overlay)?.visibility =
                        View.VISIBLE
                }
            } else {
                if (binding.stickyTabContainer.visibility == View.VISIBLE) {
                    binding.stickyTabContainer.visibility = View.GONE
                    binding.originalTabContainer.visibility = View.VISIBLE
                    binding.toolbar.toolbar.setBackgroundColor(
                        ContextCompat.getColor(
                            this,
                            android.R.color.transparent
                        )
                    )

                    // 그라데이션 View 숨김
                    binding.root.findViewById<View>(R.id.gradient_sticky_overlay)?.visibility =
                        View.GONE
                }
            }
        }

        setupTabListeners()
        selectTab(currentTab, withAnimation = false)
        setupBottomSheetCallbacks()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val result = RecipeLoader.loadSingleRecipeWithAuthor(recipeId!!)
            result.onSuccess { recipe ->
                if (recipe != null) {
                    currentRecipe = recipe
                    // 조회수 증가 로직 호출
                    incrementViewCount(recipe)
                    displayHeaderInfo(recipe)
                    populateSummaryTab(recipe)
                    loadReviews(recipe.id!!)
                    setupInteractionButtons(recipe)
                } else {
                    Toast.makeText(this@RecipeDetailActivity, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT)
                        .show()
                    finish()
                }
            }.onFailure {
                Toast.makeText(
                    this@RecipeDetailActivity,
                    "데이터 로딩 실패: ${it.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    // [수정] 조회수 증가 로직 전체 변경
    private fun incrementViewCount(recipe: Recipe) {
        val currentUser = auth.currentUser ?: return // 비로그인 사용자는 조회수 증가 안 함
        val userId = currentUser.uid
        val recipeRef = firestore.collection("Recipes").document(recipe.id!!)

        // 최근 본 레시피 목록에는 항상 저장
        saveToRecentlyViewed(recipe)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(recipeRef)
            // [수정] viewedBy 배열 대신 viewTimestamps 맵 사용
            val viewTimestamps = snapshot.get("viewTimestamps") as? MutableMap<String, Timestamp> ?: mutableMapOf()
            val lastViewedTimestamp = viewTimestamps[userId]

            // 24시간 전 시간 계산
            val twentyFourHoursAgo = Timestamp(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000))

            // 마지막으로 본 시간이 24시간 이전이거나, 본 기록이 없는 경우에만 조회수 증가
            if (lastViewedTimestamp == null || lastViewedTimestamp.toDate().before(twentyFourHoursAgo.toDate())) {
                // 1. 레시피 문서의 총 조회수(viewCount)와 마지막 조회시간 맵 업데이트
                transaction.update(recipeRef, "viewCount", FieldValue.increment(1))
                viewTimestamps[userId] = Timestamp.now()
                transaction.update(recipeRef, "viewTimestamps", viewTimestamps)

                // 2. 24시간 인기글 집계를 위한 별도 문서 생성
                val statsRef = firestore.collection("recipeViewStats").document()
                val viewStat = hashMapOf(
                    "recipeId" to recipe.id,
                    "userId" to userId,
                    "viewedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(statsRef, viewStat)

                return@runTransaction true // 조회수 증가가 일어났음을 의미
            }

            return@runTransaction false // 조회수 증가가 일어나지 않았음을 의미
        }.addOnSuccessListener { didIncrement ->
            if (didIncrement) {
                Log.d("ViewCount", "조회수 증가 성공")
                // 로컬 데이터 및 UI 즉시 업데이트
                val newViewCount = (recipe.viewCount ?: 0) + 1
                currentRecipe?.viewCount = newViewCount
                binding.tvViewCount.text = "조회수 ${"%,d".format(newViewCount)}"
            } else {
                Log.d("ViewCount", "24시간 이내에 이미 조회하여 조회수 유지.")
            }
        }.addOnFailureListener { e ->
            Log.w("ViewCount", "조회수 트랜잭션 실패", e)
        }
    }

    private fun saveToRecentlyViewed(recipe: Recipe) {
        // 최근 본 레시피에 저장
        RecentlyViewedManager.addRecentlyViewed(
            context = this,
            recipeId = recipe.id ?: return,
            title = recipe.title ?: "제목 없음",
            thumbnailUrl = recipe.thumbnailUrl
        )
    }

    private fun displayHeaderInfo(recipe: Recipe) {
        Glide.with(this).load(recipe.thumbnailUrl).into(binding.headerImage)
        binding.title.text = recipe.title
        binding.textViewCookingTime.text = "${recipe.cookingTime ?: 0}분"
        binding.textViewDifficulty.text = recipe.difficulty
        binding.textViewLikeCount.text = (recipe.likeCount ?: 0).toString()

        recipe.createdAt?.toDate()?.let {
            val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN)
            binding.tvCreationDate.text = sdf.format(it)
        }
        binding.tvViewCount.text = "조회수 ${"%,d".format(recipe.viewCount ?: 0)}"
    }

    private fun populateSummaryTab(recipe: Recipe) {
        val summaryContainer = binding.scrollViewSummaryContent
        summaryContainer.removeAllViews()
        val summaryBinding = ContentRecipeSummaryBinding.inflate(layoutInflater)
        val authorInfoView = layoutInflater.inflate(R.layout.item_author_info, summaryBinding.authorInfoLayout, false)
        recipe.author?.let { author ->
            val tvAuthorTitle: TextView = authorInfoView.findViewById(R.id.tv_author_title)
            val tvAuthorName: TextView = authorInfoView.findViewById(R.id.tv_author_name)
            val ivAuthorProfile: de.hdodenhof.circleimageview.CircleImageView = authorInfoView.findViewById(R.id.iv_author_profile)
            if (!author.title.isNullOrEmpty()) {
                tvAuthorTitle.visibility = View.VISIBLE
                tvAuthorTitle.text = author.title
            } else {
                tvAuthorTitle.visibility = View.GONE
            }
            tvAuthorName.text = author.nickname ?: "작성자 정보 없음"
            if (!author.profileImageUrl.isNullOrEmpty()) {
                Glide.with(this).load(author.profileImageUrl).into(ivAuthorProfile)
            } else {
                ivAuthorProfile.setImageResource(R.drawable.ic_profile_placeholder)
            }
        }
        summaryBinding.authorInfoLayout.addView(authorInfoView)
        summaryBinding.authorInfoLayout.setOnClickListener {
            val intent = Intent(this, UserFeedActivity::class.java).apply { putExtra(UserFeedActivity.EXTRA_USER_ID, recipe.userId) }
            startActivity(intent)
        }
        summaryBinding.tvRecipeSimpleDescription.text = recipe.simpleDescription
        summaryBinding.ingredientsContainer.removeAllViews()
        recipe.ingredients?.forEach { ingredient ->
            val view = layoutInflater.inflate(R.layout.item_ingredient_display, summaryBinding.ingredientsContainer, false)
            view.findViewById<TextView>(R.id.ingredient_name).text = ingredient.name
            view.findViewById<TextView>(R.id.ingredient_amount).text = "${ingredient.amount ?: ""}${ingredient.unit ?: ""}"
            summaryBinding.ingredientsContainer.addView(view)
        }
        summaryBinding.toolsContainer.removeAllViews()
        var tools = recipe.tools // tools가 null이거나 비어있는 경우 안보이게
        if (tools.isNullOrEmpty()) {
            summaryBinding.sectionTools.visibility = View.GONE
        }
        else {
            summaryBinding.sectionTools.visibility = View.VISIBLE
            tools.forEach { tool ->
                val view = layoutInflater.inflate(R.layout.item_tool_display, summaryBinding.toolsContainer, false)
                view.findViewById<TextView>(R.id.tool_name).text = tool
                summaryBinding.toolsContainer.addView(view)
            }
        }
        /*summaryBinding.btnStartCooking.setOnClickListener {
            val intent = Intent(this, RecipeReadActivity::class.java).apply { putExtra("RECIPE_ID", recipeId) }
            resultLauncher.launch(intent)
        }*/
        summaryContainer.addView(summaryBinding.root)
    }

    private fun loadReviews(recipeId: String) {
        binding.progressBarLoading.visibility = View.VISIBLE
        firestore.collection("Recipes").document(recipeId).collection("Reviews").get()
            .addOnSuccessListener { snapshot ->
                binding.progressBarLoading.visibility = View.GONE
                val reviews = snapshot.toObjects(Review::class.java)
                val reviewCountText = "리뷰 (${reviews.size})"
                binding.btnPageReview.text = reviewCountText
                binding.btnPageReviewSticky.text = reviewCountText
                if (reviews.isNotEmpty()) {
                    binding.textViewEmpty.visibility = View.GONE
                    binding.recyclerViewReviews.visibility = View.VISIBLE
                    val avgRating = reviews.mapNotNull { it.rating }.average()
                    binding.textViewRateAVG.text = if (avgRating.isNaN()) "0.0" else String.format("%.1f", avgRating)
                } else {
                    binding.textViewEmpty.text = "작성된 후기가 없습니다.\n첫 번째로 후기를 남겨 보세요!"
                    binding.textViewEmpty.visibility = View.VISIBLE
                    binding.recyclerViewReviews.visibility = View.GONE
                    binding.textViewRateAVG.text = "0.0"
                }
                reviewAdapter.updateReviews(reviews)
            }
    }

    private fun setupInteractionButtons(recipe: Recipe) {
        val currentUser = auth.currentUser
        val editDeleteContainer = binding.toolbar.root.findViewById<LinearLayout>(R.id.edit_delete_container)
        val btnEditRecipe = binding.toolbar.root.findViewById<View>(R.id.edit_button_card)
        val btnDeleteRecipe = binding.toolbar.root.findViewById<View>(R.id.delete_button_card)
        if (currentUser != null && currentUser.uid == recipe.userId) {
            editDeleteContainer.visibility = View.VISIBLE
            btnEditRecipe.visibility = View.VISIBLE
            btnDeleteRecipe.visibility = View.VISIBLE
            btnEditRecipe.setOnClickListener {
                val intent = Intent(this, RecipeEditActivity::class.java).putExtra("RECIPE_ID", recipeId)
                resultLauncher.launch(intent)
            }
            btnDeleteRecipe.setOnClickListener { showDeleteConfirmationDialog() }
        } else {
            editDeleteContainer.visibility = View.GONE
        }
        updateBookmarkButton(recipe.isBookmarked)
        binding.btnBookmark.setOnClickListener {
            if (currentUser == null) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleBookmark()
        }
        binding.btnStartCooking.setOnClickListener {
            val intent = Intent(this, RecipeReadActivity::class.java).apply { putExtra("RECIPE_ID", recipeId) }
            resultLauncher.launch(intent)
        }
        updateLikeButton(recipe.isLiked)
        binding.btnLike.setOnClickListener {
            if (currentUser == null) {
                Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            } else if (currentUser.uid == recipe.userId) {
                Toast.makeText(this, "자신의 글에는 '좋아요'를 누를 수 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                toggleLike()
            }
        }
    }

    private fun toggleBookmark() {
        val recipe = currentRecipe ?: return
        val currentUser = auth.currentUser ?: return
        BookmarkManager.toggleBookmark(recipe.id!!, currentUser.uid, recipe.isBookmarked) { result ->
            result.onSuccess { newState ->
                recipe.isBookmarked = newState
                updateBookmarkButton(newState)
                val message = if (newState) "북마크에 추가되었습니다." else "북마크가 해제되었습니다."
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleLike() {
        val recipe = currentRecipe ?: return
        val currentUser = auth.currentUser ?: return
        val recipeRef = firestore.collection("Recipes").document(recipe.id!!)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(recipeRef)
            val currentLikes = snapshot.get("likedBy") as? List<String> ?: emptyList()
            val isCurrentlyLiked = currentLikes.contains(currentUser.uid)

            if (isCurrentlyLiked) {
                // 좋아요 취소
                transaction.update(recipeRef, "likedBy", FieldValue.arrayRemove(currentUser.uid))
                transaction.update(recipeRef, "likeCount", FieldValue.increment(-1))
            } else {
                // 좋아요
                transaction.update(recipeRef, "likedBy", FieldValue.arrayUnion(currentUser.uid))
                transaction.update(recipeRef, "likeCount", FieldValue.increment(1))
            }
            // 트랜잭션 결과로 새로운 좋아요 상태를 반환
            !isCurrentlyLiked
        }.addOnSuccessListener { newIsLiked ->
            // 트랜잭션의 반환값(새로운 좋아요 상태)을 사용
            val oldLikeCount = recipe.likeCount ?: 0
            val newLikeCount = if (newIsLiked) oldLikeCount + 1 else oldLikeCount - 1
            recipe.isLiked = newIsLiked
            recipe.likeCount = newLikeCount
            updateLikeButton(newIsLiked)
            binding.textViewLikeCount.text = newLikeCount.toString()

            // 알림 핸들러 호출 로직 수정
            val recipientId = recipe.userId
            val senderId = currentUser.uid

            if (recipientId != null) {
                lifecycleScope.launch {
                    if (newIsLiked) {
                        // 좋아요를 눌렀을 때만 '추가' 함수 호출
                        NotificationHandler.addLikeReviewNotification(
                            recipientId = recipientId,
                            senderId = senderId,
                            recipe = recipe,
                            type = NotificationType.LIKE
                        )
                    } else {
                        // 좋아요를 취소했을 때 '삭제' 함수 호출
                        NotificationHandler.removeLikeReviewNotification(
                            recipientId = recipientId,
                            senderId = senderId,
                            recipeId = recipe.id!!,
                            type = NotificationType.LIKE
                        )
                    }
                }
            } else {
                Log.w(TAG, "recipientId가 null이어서 알림을 생성/삭제할 수 없습니다.")
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "좋아요 처리 실패", e)
            Toast.makeText(this, "좋아요 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBookmarkButton(isBookmarked: Boolean) {
        if (isBookmarked) {
            binding.iconBookmark.visibility = View.GONE
            binding.iconBookmarkFilled.visibility = View.VISIBLE
            binding.textViewBookmarkStatus.text = "저장됨"
        } else {
            binding.iconBookmark.visibility = View.VISIBLE
            binding.iconBookmarkFilled.visibility = View.GONE
            binding.textViewBookmarkStatus.text = "저장하기"
        }
    }

    private fun updateLikeButton(isLiked: Boolean) {
        if (isLiked) {
            binding.iconLike.visibility = View.GONE
            binding.iconLikeFilled.visibility = View.VISIBLE
        } else {
            binding.iconLike.visibility = View.VISIBLE
            binding.iconLikeFilled.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("레시피 삭제")
            .setMessage("이 레시피를 정말로 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("삭제") { _, _ -> currentRecipe?.let { deleteRecipeProcess(it) } }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteRecipeProcess(recipe: Recipe) {
        deleteRecipeImages(recipe) { isSuccess ->
            if (isSuccess) {
                firestore.collection("Recipes").document(recipe.id!!).delete()
                    .addOnSuccessListener {
                        updateUserRecipeCount()
                        Toast.makeText(this, "레시피가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e -> Toast.makeText(this, "레시피 문서 삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show() }
            } else {
                Toast.makeText(this, "이미지 삭제 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteRecipeImages(recipe: Recipe, onComplete: (Boolean) -> Unit) {
        val storage = Firebase.storage
        val imageTasks = mutableListOf<Task<Void>>()
        recipe.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let { imageTasks.add(storage.getReferenceFromUrl(it).delete()) }
        recipe.steps?.forEach { step -> step.imageUrl?.takeIf { it.isNotEmpty() }?.let { imageTasks.add(storage.getReferenceFromUrl(it).delete()) } }
        if (imageTasks.isEmpty()) { onComplete(true); return }
        Tasks.whenAll(imageTasks)
            .addOnSuccessListener { Log.d("DeleteRecipe", "모든 이미지 삭제 성공"); onComplete(true) }
            .addOnFailureListener { e -> Log.e("DeleteRecipe", "이미지 삭제 중 오류 발생", e); onComplete(false) }
    }

    private fun updateUserRecipeCount() {
        val currentUser = auth.currentUser ?: return
        val userRef = firestore.collection("Users").document(currentUser.uid)
        userRef.update("recipeCount", FieldValue.increment(-1))
            .addOnSuccessListener { Log.d("DeleteRecipe", "recipeCount 업데이트 성공") }
            .addOnFailureListener { e -> Log.w("DeleteRecipe", "recipeCount 업데이트 실패", e) }
    }

    private fun setupBottomSheetCallbacks() {
        binding.headerImage.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (isLayoutReady.compareAndSet(false, true)) {
                    val screenHeight = resources.displayMetrics.heightPixels
                    val toolbarHeight = binding.toolbar.root.height
                    val imageHeight = binding.headerImage.height
                    val peekHeight = (screenHeight - (toolbarHeight + imageHeight * (1 - IMAGE_OVERLAP_RATIO)))
                        .toInt().coerceAtLeast(dpToPx(MIN_PEEK_HEIGHT_DP))
                    bottomSheetBehavior.peekHeight = peekHeight
                    binding.bottomSheet.layoutParams.height = screenHeight
                }
            }
        })
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset >= TRANSPARENCY_START_THRESHOLD) {
                    val adjustedOffset = (slideOffset - TRANSPARENCY_START_THRESHOLD) / (1.0f - TRANSPARENCY_START_THRESHOLD)
                    binding.headerImage.alpha = 1.0f - adjustedOffset
                } else {
                    binding.headerImage.alpha = 1.0f
                }
            }
        })
    }

    private fun setupTabListeners() {
        val listener = RadioGroup.OnCheckedChangeListener { radioGroup, checkedId ->
            if (isTabSelectionInProgress) return@OnCheckedChangeListener
            val newTab = when (checkedId) {
                R.id.btn_pageSummary, R.id.btn_pageSummary_sticky -> 0
                R.id.btn_pageReview, R.id.btn_pageReview_sticky -> 1
                else -> currentTab
            }
            selectTab(newTab, withAnimation = true)
        }
        binding.framePageSelector.setOnCheckedChangeListener(listener)
        binding.framePageSelectorSticky.setOnCheckedChangeListener(listener)
    }

    private fun selectTab(tabIndex: Int, withAnimation: Boolean = true) {
        if (currentTab == tabIndex && withAnimation) return
        isTabSelectionInProgress = true
        if (tabIndex == 0) {
            binding.framePageSelector.check(R.id.btn_pageSummary)
            binding.framePageSelectorSticky.check(R.id.btn_pageSummary_sticky)
        } else {
            binding.framePageSelector.check(R.id.btn_pageReview)
            binding.framePageSelectorSticky.check(R.id.btn_pageReview_sticky)
        }
        isTabSelectionInProgress = false
        showTab(tabIndex, withAnimation)
    }

    private fun showTab(tabIndex: Int, withAnimation: Boolean = true) {
        if (currentTab == tabIndex && withAnimation) return
        val previousTab = currentTab
        currentTab = tabIndex
        if (withAnimation) {
            animateTabTransition(previousTab, currentTab)
        } else {
            setTabVisibility(previousTab, View.GONE)
            setTabVisibility(currentTab, View.VISIBLE)
        }
    }

    private fun animateTabTransition(fromTab: Int, toTab: Int) {
        val fromView = getTabView(fromTab)
        val toView = getTabView(toTab)
        val slideDirection = if (toTab > fromTab) 1 else -1
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        toView.translationX = screenWidth * slideDirection
        toView.visibility = View.VISIBLE
        toView.alpha = 0f
        fromView.animate()
            .translationX(-screenWidth * slideDirection).alpha(0f)
            .setDuration(ANIMATION_DURATION)
            .withEndAction {
                fromView.visibility = View.GONE
                fromView.translationX = 0f
                fromView.alpha = 1f
            }.start()
        toView.animate()
            .translationX(0f).alpha(1f)
            .setDuration(ANIMATION_DURATION)
            .start()
    }

    private fun getTabView(tabIndex: Int): View {
        return when (tabIndex) {
            0 -> binding.scrollViewSummaryContent
            1 -> binding.layoutReviewContent
            else -> binding.scrollViewSummaryContent
        }
    }

    private fun setTabVisibility(tabIndex: Int, visibility: Int) {
        getTabView(tabIndex).visibility = visibility
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}