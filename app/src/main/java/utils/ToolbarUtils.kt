package utils

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.example.recipe_pocket.R
import com.example.recipe_pocket.ui.main.MainActivity

object ToolbarUtils {

    // 기본 툴바
    fun setupTransparentToolbar(
        activity: Activity,
        title: String = "",
        onBackPressed: (() -> Unit)? = null,
        navigateToMainActivity: Boolean = false,
        showEditButton: Boolean = false,
        showDeleteButton: Boolean = false,
        onEditClicked: (() -> Unit)? = null,
        onDeleteClicked: (() -> Unit)? = null
    ) {
        setupCommonToolbar(activity, title, onBackPressed, navigateToMainActivity)
        setupActionButtons(activity, showEditButton, showDeleteButton, onEditClicked, onDeleteClicked)
    }

    // 글쓰기 툴바
    fun setupWriteToolbar(
        activity: Activity,
        title: String = "",
        onBackPressed: (() -> Unit)? = null,
        navigateToMainActivity: Boolean = false,
        onTempSaveClicked: (() -> Unit)? = null,
        onSaveClicked: (() -> Unit)? = null
    ) {
        setupCommonToolbar(activity, title, onBackPressed, navigateToMainActivity)
        setupWriteButtons(activity, onTempSaveClicked, onSaveClicked)
    }

    // 공통 툴바 설정
    private fun setupCommonToolbar(
        activity: Activity,
        title: String,
        onBackPressed: (() -> Unit)?,
        navigateToMainActivity: Boolean
    ) {
        // 상태바 색
        activity.window.statusBarColor = Color.TRANSPARENT
        // 상태표시줄 텍스트 색상 (어두운 배경이면 false, 밝은 배경이면 true)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars = true

        // 툴바 표시 텍스트 설정
        val toolbarTitle = activity.findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle?.text = title

        // 뒤로가기 버튼 설정
        setupBackButton(activity, onBackPressed, navigateToMainActivity)

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

    // 스크롤 리스너 설정 (배경 페이드 효과)
    fun setupScrollListener(activity: Activity, scrollView: androidx.core.widget.NestedScrollView) {
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

    // 뒤로가기 버튼
    private fun setupBackButton(
        activity: Activity,
        onBackPressed: (() -> Unit)?,
        navigateToMainActivity: Boolean
    ) {
        val backButton = activity.findViewById<ImageButton>(R.id.back_button)
        backButton?.setOnClickListener {
            when {
                onBackPressed != null -> onBackPressed.invoke()
                navigateToMainActivity -> {
                    val intent = Intent(activity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    activity.startActivity(intent)
                }
                else -> activity.finish()
            }
        }
    }

    // 기본 액션 버튼들 설정 (수정/삭제)
    private fun setupActionButtons(
        activity: Activity,
        showEditButton: Boolean,
        showDeleteButton: Boolean,
        onEditClicked: (() -> Unit)?,
        onDeleteClicked: (() -> Unit)?
    ) {
        // 수정 버튼
        val editButtonCard = activity.findViewById<androidx.cardview.widget.CardView>(R.id.edit_button_card)
        val editButton = activity.findViewById<ImageButton>(R.id.edit_button)

        if (showEditButton) {
            editButtonCard?.visibility = View.VISIBLE
            editButton?.setOnClickListener { onEditClicked?.invoke() }
        } else {
            editButtonCard?.visibility = View.GONE
        }

        // 삭제 버튼
        val deleteButtonCard = activity.findViewById<androidx.cardview.widget.CardView>(R.id.delete_button_card)
        val deleteButton = activity.findViewById<ImageButton>(R.id.delete_button)

        if (showDeleteButton) {
            deleteButtonCard?.visibility = View.VISIBLE
            deleteButton?.setOnClickListener { onDeleteClicked?.invoke() }
        } else {
            deleteButtonCard?.visibility = View.GONE
        }
    }

    // 작성 버튼들 설정 (임시저장/등록)
    private fun setupWriteButtons(
        activity: Activity,
        onTempSaveClicked: (() -> Unit)?,
        onSaveClicked: (() -> Unit)?
    ) {
        // 임시저장 버튼
        val tempSaveButton = activity.findViewById<TextView>(R.id.btn_temp_save)
        tempSaveButton?.setOnClickListener { onTempSaveClicked?.invoke() }

        // 등록 버튼
        val saveButton = activity.findViewById<TextView>(R.id.btn_save)
        saveButton?.setOnClickListener { onSaveClicked?.invoke() }
    }
}