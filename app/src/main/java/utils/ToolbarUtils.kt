package utils

import android.app.Activity
import android.graphics.Color
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.example.recipe_pocket.R

object ToolbarUtils {

    fun setupTransparentToolbar(activity: Activity, title: String = "", onBackPressed: (() -> Unit)? = null) {
        // 상태바 색
        activity.window.statusBarColor = Color.TRANSPARENT
        // 상태표시줄 텍스트 색상 (어두운 배경이면 false, 밝은 배경이면 true)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars = true

        // 툴바 표시 텍스트 설정
        val toolbarTitle = activity.findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = title

        // 뒤로가기 버튼 설정
        setupBackButton(activity, onBackPressed)

        // 툴바 상태바 높이만큼 보정
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams {
                height = statusBarHeight + (56 * activity.resources.displayMetrics.density).toInt()
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    // 스크롤 리스너 설정 메서드
    private fun setupScrollListener(activity: Activity, scrollView: androidx.core.widget.NestedScrollView) {
        val backButtonCard = activity.findViewById<androidx.cardview.widget.CardView>(R.id.back_button_card)

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val threshold = 50 // 50dp 스크롤하면 배경 나타나기 시작
            val maxScroll = 100 // 100dp에서 완전히 나타남

            val progress = when {
                scrollY < threshold -> 0f
                scrollY > maxScroll -> 1f
                else -> (scrollY - threshold).toFloat() / (maxScroll - threshold)
            }

            // 배경색의 알파값만 조절 (아이콘은 그대로 유지)
            val backgroundColor = android.graphics.Color.argb(
                (progress * 255).toInt(), // 알파값만 변경
                255, 255, 255 // RGB는 흰색으로 고정
            )

            backButtonCard?.setCardBackgroundColor(backgroundColor)
        }
    }

    // 뒤로가기 버튼 설정 메서드
    private fun setupBackButton(activity: Activity, onBackPressed: (() -> Unit)? = null) {
        val backButton = activity.findViewById<ImageButton>(R.id.back_button)
        backButton?.setOnClickListener {
            onBackPressed?.invoke() ?: activity.finish()
        }
    }
}