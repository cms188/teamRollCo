package com.example.recipe_pocket

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class NotificationActivity : AppCompatActivity() {

    private lateinit var toolbar: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyLayout: View
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        // 툴바 표시 텍스트 설정
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = "알림"

        // 툴바 상태바 높이만큼 보정
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams {
                height = statusBarHeight + (56 * resources.displayMetrics.density).toInt()
            }
            WindowInsetsCompat.CONSUMED
        }

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        // 뷰 초기화
        toolbar = findViewById(R.id.toolbar)
        swipeRefresh = findViewById(R.id.swipe_refresh)
        recyclerView = findViewById(R.id.recycler_view)
        emptyLayout = findViewById(R.id.layout_empty_notification)
        progressBar = findViewById(R.id.progress_bar)

        // RecyclerView 기본 설정
        recyclerView.layoutManager = LinearLayoutManager(this)
        // recyclerView.adapter = YourAdapter() // 어댑터 설정
    }

    private fun setupClickListeners() {
        // SwipeRefreshLayout 새로고침 이벤트
        swipeRefresh.setOnRefreshListener {
            onRefreshTriggered()
        }

        // RecyclerView 아이템 클릭은 어댑터에서 처리

    }

    private fun onRefreshTriggered() {
        // 새로고침 시 동작
        // 예: 데이터 다시 로드

        // 새로고침 완료 후 호출 (예시)
        swipeRefresh.postDelayed({
            swipeRefresh.isRefreshing = false
            loadNotificationData()
        }, 2000)
    }

    // RecyclerView 어댑터에서 사용할 클릭 인터페이스
    interface OnNotificationItemClickListener {
        fun onItemClick(position: Int, item: Any) // 실제 데이터 타입으로 변경
        fun onItemLongClick(position: Int, item: Any): Boolean
    }

    // 데이터 로드 메서드
    private fun loadNotificationData() {
        // 로딩 표시
        showLoading(true)

        // 실제 데이터 로드
        // 데이터베이스 조회

        // 로딩 완료 후
        showLoading(false)
        updateUI(hasData = true) // 데이터 유무에 따라 변경
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateUI(hasData: Boolean) {
        if (hasData) {
            recyclerView.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
        } else {
            recyclerView.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
        }
    }

}