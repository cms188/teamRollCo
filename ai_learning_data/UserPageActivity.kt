package com.example.recipe_pocket

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

class UserPageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_userpage)

        // 툴바 표시 텍스트 설정
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = "MY"

        // 툴바 버튼 숨김
        val toolbarbtn = findViewById<ImageButton>(R.id.back_button)
        toolbarbtn.visibility = View.GONE

        // 툴바 상태바 높이만큼 보정
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams { height = statusBarHeight + dpToPx(56) }
            WindowInsetsCompat.CONSUMED
        }

        // 클릭 리스너 설정
        setupClickListeners()

    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }




    private fun setupClickListeners() {
        // 프로필 클릭
        findViewById<TextView>(R.id.imageView_profile).setOnClickListener {

        }

        // 정보 수정 클릭
        findViewById<TextView>(R.id.textView_editInfo).setOnClickListener {

        }

        // 칭호 클릭
        findViewById<CardView>(R.id.cardView_badge).setOnClickListener {

        }

        // 내 레시피 클릭
        findViewById<LinearLayout>(R.id.layout_myRecipes).setOnClickListener {

        }

        // 팔로워 클릭
        findViewById<LinearLayout>(R.id.layout_followers).setOnClickListener {

        }

        // 팔로잉 클릭
        findViewById<LinearLayout>(R.id.layout_following).setOnClickListener {

        }

        // -----------------------

        // 북마크 클릭
        findViewById<LinearLayout>(R.id.layout_bookmark).setOnClickListener {

        }

        // 좋아요 클릭
        findViewById<LinearLayout>(R.id.layout_like).setOnClickListener {

        }

        // 리뷰 관리 클릭
        findViewById<LinearLayout>(R.id.layout_reviewManage).setOnClickListener {

        }

        // 최근 본 레시피 클릭
        findViewById<LinearLayout>(R.id.layout_recentRecipe).setOnClickListener {

        }

        // -----------------------

        /*
        // 테마 클릭
        findViewById<LinearLayout>(R.id.layout_theme).setOnClickListener {

        }

        // 메뉴 클릭
        findViewById<LinearLayout>(R.id.layout_none).setOnClickListener {

        }
        */

    }
}