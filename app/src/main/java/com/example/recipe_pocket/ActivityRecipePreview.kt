package com.example.recipe_pocket

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior

class ActivityRecipePreview : AppCompatActivity() {

    private companion object {
        const val TOOLBAR_HEIGHT_DP = 56                // 툴바 기본 높이
        const val IMAGE_OVERLAP_RATIO = 0.4f            // 이미지와 바텀시트 겹침 비율
        const val MIN_PEEK_HEIGHT_DP = 300              // 바텀시트 최소 peek 높이
        const val TRANSPARENCY_START_THRESHOLD = 0.7f   // 투명도 변경 시작 지점 (%)
        const val ANIMATION_DURATION = 150L             // 애니메이션 지속 시간 (ms)
    }

    private lateinit var headerImage: ImageView         // 헤더 이미지뷰

    // 탭 관련 뷰
    private lateinit var framePageSelector: RadioGroup
    private lateinit var btnPageSummary: RadioButton
    private lateinit var btnPageReview: RadioButton
    private lateinit var scrollViewSummary: ScrollView
    private lateinit var layoutReviewContent: LinearLayout

    // 현재 선택된 탭 (0: 요약, 1: 리뷰)
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recipe_preview)

        // 툴바 화면 텍스트 이름 비우기
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = ""

        initViews()
        setupUI()
        setupTabSelector()
    }

    // 뷰 초기화
    private fun initViews() {
        btnPageSummary = findViewById(R.id.btn_pageSummary)
        btnPageReview = findViewById(R.id.btn_pageReview)
        framePageSelector = findViewById(R.id.frame_pageSelector)
        scrollViewSummary = findViewById(R.id.scrollView_summaryContent)
        layoutReviewContent = findViewById(R.id.layout_reviewContent)
    }

    // UI 초기화 및 설정
    private fun setupUI() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        headerImage = findViewById(R.id.headerImage)
        val bottomSheet = findViewById<LinearLayout>(R.id.bottom_sheet)
        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        setupToolbar(toolbar)

        // 바텀시트 위치 설정 (이미지 로드 후)
        setupBottomSheet(toolbar, headerImage, bottomSheet, bottomSheetBehavior)

        // 바텀시트 콜백 설정 (이미지 색상 변경)
        setupBottomSheetCallbacks(bottomSheetBehavior, headerImage)
    }

    // 탭 선택기 설정
    private fun setupTabSelector() {
        // 초기 상태에 보여줄 탭 설정
        showTab(1, false) // 애니메이션 없이 탭 표시

        framePageSelector.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.btn_pageSummary -> {
                    if (currentTab != 0) {
                        showTab(0, true)
                    }
                }
                R.id.btn_pageReview -> {
                    if (currentTab != 1) {
                        showTab(1, true)
                    }
                }
            }
        }
    }

    // 탭 전환 메서드
    private fun showTab(tabIndex: Int, withAnimation: Boolean) {
        if (currentTab == tabIndex) return

        val previousTab = currentTab
        currentTab = tabIndex

        if (withAnimation) {
            animateTabTransition(previousTab, currentTab)
        } else {
            // 애니메이션 없이 즉시 전환
            setTabVisibility(previousTab, View.GONE)
            setTabVisibility(currentTab, View.VISIBLE)
        }
    }

    // 탭 전환 애니메이션
    private fun animateTabTransition(fromTab: Int, toTab: Int) {
        val fromView = getTabView(fromTab)
        val toView = getTabView(toTab)

        // 슬라이드 방향 결정 (왼쪽에서 오른쪽: 요약→리뷰, 오른쪽에서 왼쪽: 리뷰→요약)
        val slideDirection = if (toTab > fromTab) 1 else -1
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        // 새로운 뷰를 화면 밖에서 시작
        toView.translationX = screenWidth * slideDirection
        toView.visibility = View.VISIBLE
        toView.alpha = 1f

        // 현재 뷰를 화면 밖으로 슬라이드
        ObjectAnimator.ofFloat(fromView, "translationX", 0f, -screenWidth * slideDirection).apply {
            duration = ANIMATION_DURATION
            start()
        }

        // 현재 뷰 페이드 아웃
        ObjectAnimator.ofFloat(fromView, "alpha", 1f, 0f).apply {
            duration = ANIMATION_DURATION
            start()
        }

        // 새로운 뷰를 제자리로 슬라이드
        ObjectAnimator.ofFloat(toView, "translationX", screenWidth * slideDirection, 0f).apply {
            duration = ANIMATION_DURATION
            start()
        }

        // 새로운 뷰 페이드 인
        ObjectAnimator.ofFloat(toView, "alpha", 0f, 1f).apply {
            duration = ANIMATION_DURATION
            start()
        }

        // 애니메이션 완료 후 이전 뷰 숨기기
        toView.postDelayed({
            fromView.visibility = View.GONE
            fromView.translationX = 0f
            fromView.alpha = 1f
        }, ANIMATION_DURATION)
    }

    // 탭에 해당하는 뷰 반환
    private fun getTabView(tabIndex: Int): View {
        return when (tabIndex) {
            0 -> scrollViewSummary
            1 -> layoutReviewContent
            else -> scrollViewSummary
        }
    }

    // 탭 가시성 설정
    private fun setTabVisibility(tabIndex: Int, visibility: Int) {
        when (tabIndex) {
            0 -> scrollViewSummary.visibility = visibility
            1 -> layoutReviewContent.visibility = visibility
        }
    }

    // 툴바 상태바 처리
    private fun setupToolbar(toolbar: Toolbar) {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            // 툴바 위쪽에 상태바 높이 만큼 패딩 추가
            view.updatePadding(top = statusBarHeight)

            // 툴바 전체 높이 = 상태바 높이 + 툴바 높이
            view.updateLayoutParams {
                height = statusBarHeight + dpToPx(TOOLBAR_HEIGHT_DP)
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    // 바텀시트 위치와 동작 설정
    // 이미지가 완전히 로드된 후 실제 높이를 기준으로 바텀시트 위치 계산
    private fun setupBottomSheet(
        toolbar: Toolbar,
        headerImage: ImageView,
        bottomSheet: LinearLayout,
        bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    ) {
        // 이미지 레이아웃 완료 후 실행
        headerImage.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                headerImage.viewTreeObserver.removeOnGlobalLayoutListener(this)

                // 화면 및 뷰 크기 정보 수집
                val screenHeight = resources.displayMetrics.heightPixels
                val toolbarHeight = toolbar.height
                val imageHeight = headerImage.height

                // 바텀시트 peek height 계산
                val peekHeight = calculatePeekHeight(screenHeight, toolbarHeight, imageHeight)

                // 바텀시트 동작 설정
                configureBehavior(bottomSheetBehavior, peekHeight, toolbarHeight)

                // 바텀시트 최대 높이 제한
                limitMaxHeight(bottomSheet, screenHeight, toolbarHeight)
            }
        })
    }

    //바텀시트 peek height 계산
    // 이미지와 40% 겹치도록 위치 조정
    private fun calculatePeekHeight(screenHeight: Int, toolbarHeight: Int, imageHeight: Int): Int {
        // 이미지 하단 절대 위치 = 툴바 높이 + 이미지 높이
        val imageBottomPosition = toolbarHeight + imageHeight

        // 겹침 오프셋 = 이미지 높이 × 겹침 비율
        val overlapOffset = (imageHeight * IMAGE_OVERLAP_RATIO).toInt()

        // 바텀시트 시작 위치 = 이미지 하단 - 겹침 오프셋
        val startPosition = imageBottomPosition - overlapOffset

        // peek height = 화면 높이 - 시작 위치
        val calculatedHeight = screenHeight - startPosition

        // 최소 높이 보장
        return calculatedHeight.coerceAtLeast(dpToPx(MIN_PEEK_HEIGHT_DP))
    }

    // 바텀시트 동작 설정
    private fun configureBehavior(
        behavior: BottomSheetBehavior<LinearLayout>,
        peekHeight: Int,
        toolbarHeight: Int
    ) {
        behavior.apply {
            this.peekHeight = peekHeight          // 초기 표시 높이
            expandedOffset = toolbarHeight        // 확장 높이 제한
            isHideable = false                    // 숨기기 불가
        }
    }

    // 바텀시트 높이 제한
    private fun limitMaxHeight(bottomSheet: LinearLayout, screenHeight: Int, toolbarHeight: Int) {
        bottomSheet.updateLayoutParams {
            height = screenHeight - toolbarHeight
        }
    }

    // 바텀시트 스크롤 시 이미지 투명도 변경
    private fun setupBottomSheetCallbacks(bottomSheetBehavior: BottomSheetBehavior<LinearLayout>, headerImage: ImageView) {
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {

            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // slideOffset: 바텀시트가 peekHeight에서 expandedOffset까지 이동한 비율 (0.0 ~ 1.0)

                if (slideOffset >= TRANSPARENCY_START_THRESHOLD) {
                    // 투명도 변경 애니메이션
                    val adjustedOffset = (slideOffset - TRANSPARENCY_START_THRESHOLD) / (1.0f - TRANSPARENCY_START_THRESHOLD)
                    headerImage.alpha = 1.0f - adjustedOffset
                } else {
                    // 투명도 100%
                    headerImage.alpha = 1.0f
                }
            }
        })
    }

    // dp를 px로 변환
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}