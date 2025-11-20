package com.example.recipe_pocket

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import utils.ToolbarUtils

class NotificationActivity : AppCompatActivity() {

    // UI 요소들
    private lateinit var tabNewNotifications: LinearLayout
    private lateinit var tabPreviousNotifications: LinearLayout
    private lateinit var textTabNew: TextView
    private lateinit var textTabPrevious: TextView
    private lateinit var badgeNewCount: CardView
    private lateinit var textNewCount: TextView

    // 컨텐츠 컨테이너
    private lateinit var swipeRefreshNew: SwipeRefreshLayout
    private lateinit var swipeRefreshPrevious: SwipeRefreshLayout

    // 새 알림 관련
    private lateinit var layoutNewNotifications: LinearLayout
    private lateinit var layoutEmptyNewNotifications: LinearLayout
    private lateinit var layoutLoadingNew: LinearLayout
    private lateinit var recyclerViewNewNotifications: RecyclerView

    // 이전 알림 관련
    private lateinit var layoutPreviousNotifications: LinearLayout
    private lateinit var layoutEmptyPreviousNotifications: LinearLayout
    private lateinit var layoutLoadingPrevious: LinearLayout
    private lateinit var recyclerViewPreviousNotifications: RecyclerView

    // 상태 관리
    private var currentTab = TAB_NEW_NOTIFICATIONS
    private var newNotificationCount = 0

    companion object {
        private const val TAB_NEW_NOTIFICATIONS = 0
        private const val TAB_PREVIOUS_NOTIFICATIONS = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_type2)

        initViews()
        ToolbarUtils.setupTransparentToolbar(this, "알림")
        setupTabs()
        setupSwipeRefresh()
        setupRecyclerViews()

        // 초기 데이터 로드
        loadNotifications()
    }

    private fun initViews() {
        // 탭 관련
        tabNewNotifications = findViewById(R.id.tab_new_notifications)
        tabPreviousNotifications = findViewById(R.id.tab_previous_notifications)
        textTabNew = findViewById(R.id.text_tab_new)
        textTabPrevious = findViewById(R.id.text_tab_previous)
        badgeNewCount = findViewById(R.id.badge_new_count)
        textNewCount = findViewById(R.id.text_new_count)

        // 컨텐츠 컨테이너
        swipeRefreshNew = findViewById(R.id.swipe_refresh_new)
        swipeRefreshPrevious = findViewById(R.id.swipe_refresh_previous)

        // 새 알림 관련
        layoutNewNotifications = findViewById(R.id.layout_new_notifications)
        layoutEmptyNewNotifications = findViewById(R.id.layout_empty_new_notifications)
        layoutLoadingNew = findViewById(R.id.layout_loading_new)
        recyclerViewNewNotifications = findViewById(R.id.recycler_view_new_notifications)

        // 이전 알림 관련
        layoutPreviousNotifications = findViewById(R.id.layout_previous_notifications)
        layoutEmptyPreviousNotifications = findViewById(R.id.layout_empty_previous_notifications)
        layoutLoadingPrevious = findViewById(R.id.layout_loading_previous)
        recyclerViewPreviousNotifications = findViewById(R.id.recycler_view_previous_notifications)
    }

    private fun setupTabs() {
        // 새 알림 탭 클릭
        tabNewNotifications.setOnClickListener {
            selectTab(TAB_NEW_NOTIFICATIONS)
        }

        // 이전 알림 탭 클릭
        tabPreviousNotifications.setOnClickListener {
            selectTab(TAB_PREVIOUS_NOTIFICATIONS)
        }

        // 초기 탭 설정
        selectTab(TAB_NEW_NOTIFICATIONS)
    }

    private fun selectTab(tabIndex: Int) {
        currentTab = tabIndex

        when (tabIndex) {
            TAB_NEW_NOTIFICATIONS -> {
                // 탭 스타일 변경
                updateTabAppearance(true, false)

                // 컨텐츠 가시성 변경
                swipeRefreshNew.visibility = View.VISIBLE
                swipeRefreshPrevious.visibility = View.GONE
            }

            TAB_PREVIOUS_NOTIFICATIONS -> {
                // 탭 스타일 변경
                updateTabAppearance(false, true)

                // 컨텐츠 가시성 변경
                swipeRefreshNew.visibility = View.GONE
                swipeRefreshPrevious.visibility = View.VISIBLE
            }
        }
    }

    private fun updateTabAppearance(newSelected: Boolean, previousSelected: Boolean) {
        // 새 알림 탭
        if (newSelected) {
            tabNewNotifications.setBackgroundResource(R.drawable.tab_selected_background)
            textTabNew.setTextColor(ContextCompat.getColor(this, R.color.white))
        } else {
            tabNewNotifications.setBackgroundResource(R.drawable.tab_unselected_background)
            textTabNew.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        // 이전 알림 탭
        if (previousSelected) {
            tabPreviousNotifications.setBackgroundResource(R.drawable.tab_selected_background)
            textTabPrevious.setTextColor(ContextCompat.getColor(this, R.color.white))
        } else {
            tabPreviousNotifications.setBackgroundResource(R.drawable.tab_unselected_background)
            textTabPrevious.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun setupSwipeRefresh() {
        // 새 알림 새로고침
        swipeRefreshNew.setOnRefreshListener {
            loadNewNotifications()
        }

        // 이전 알림 새로고침
        swipeRefreshPrevious.setOnRefreshListener {
            loadPreviousNotifications()
        }
    }

    private fun setupRecyclerViews() {
        // 새 알림 RecyclerView 설정
        recyclerViewNewNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            // adapter = newNotificationAdapter (실제 어댑터 설정)
        }

        // 이전 알림 RecyclerView 설정
        recyclerViewPreviousNotifications.apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            // adapter = previousNotificationAdapter (실제 어댑터 설정)
        }
    }

    private fun loadNotifications() {
        loadNewNotifications()
        loadPreviousNotifications()
    }

    private fun loadNewNotifications() {
        showNewNotificationsLoading(true)

        // 실제로는 Repository나 API 호출
        // 예시: 가짜 네트워크 지연
        Handler(Looper.getMainLooper()).postDelayed({
            showNewNotificationsLoading(false)
            swipeRefreshNew.isRefreshing = false

            // 예시 데이터 - 실제로는 서버에서 받아온 데이터
            val hasNewNotifications = false // 테스트를 위해 false로 설정
            newNotificationCount = if (hasNewNotifications) 3 else 0

            updateNewNotificationBadge(newNotificationCount)
            checkNewNotificationsEmpty(hasNewNotifications)

            // RecyclerView 어댑터에 데이터 설정
            // newNotificationAdapter.updateData(notifications)

        }, 1500) // 1.5초 로딩 시뮬레이션
    }

    private fun loadPreviousNotifications() {
        showPreviousNotificationsLoading(true)

        Handler(Looper.getMainLooper()).postDelayed({
            showPreviousNotificationsLoading(false)
            swipeRefreshPrevious.isRefreshing = false

            // 예시 데이터
            val hasPreviousNotifications = false // 테스트를 위해 false로 설정

            checkPreviousNotificationsEmpty(hasPreviousNotifications)

            // RecyclerView 어댑터에 데이터 설정
            // previousNotificationAdapter.updateData(notifications)

        }, 1500)
    }

    private fun showNewNotificationsLoading(show: Boolean) {
        if (show) {
            layoutLoadingNew.visibility = View.VISIBLE
            layoutNewNotifications.visibility = View.GONE
            layoutEmptyNewNotifications.visibility = View.GONE
        } else {
            layoutLoadingNew.visibility = View.GONE
        }
    }

    private fun showPreviousNotificationsLoading(show: Boolean) {
        if (show) {
            layoutLoadingPrevious.visibility = View.VISIBLE
            layoutPreviousNotifications.visibility = View.GONE
            layoutEmptyPreviousNotifications.visibility = View.GONE
        } else {
            layoutLoadingPrevious.visibility = View.GONE
        }
    }

    private fun checkNewNotificationsEmpty(hasNotifications: Boolean) {
        if (hasNotifications) {
            layoutNewNotifications.visibility = View.VISIBLE
            layoutEmptyNewNotifications.visibility = View.GONE
        } else {
            layoutNewNotifications.visibility = View.GONE
            layoutEmptyNewNotifications.visibility = View.VISIBLE
        }
    }

    private fun checkPreviousNotificationsEmpty(hasNotifications: Boolean) {
        if (hasNotifications) {
            layoutPreviousNotifications.visibility = View.VISIBLE
            layoutEmptyPreviousNotifications.visibility = View.GONE
        } else {
            layoutPreviousNotifications.visibility = View.GONE
            layoutEmptyPreviousNotifications.visibility = View.VISIBLE
        }
    }

    private fun updateNewNotificationBadge(count: Int) {
        if (count > 0) {
            badgeNewCount.visibility = View.VISIBLE
            textNewCount.text = if (count > 99) "99+" else count.toString()
        } else {
            badgeNewCount.visibility = View.GONE
        }
    }

}