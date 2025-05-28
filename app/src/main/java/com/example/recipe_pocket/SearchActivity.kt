package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

class SearchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_result) // activity_main.xml 또는 사용 중인 메인 레이아웃 파일
        //https://developer.android.com/develop/ui/views/layout/edge-to-edge?hl=ko#kotlin
        //동작 모드 또는 버튼 모드에서 시각적 겹침을 방지
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.top_search_layout)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                topMargin = insets.top //상단도 마찬가지로 겹침 방지. 꼭 필요한 것은 아님
            }
            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
        var backButton: ImageView = findViewById<ImageView>(R.id.iv_back_button)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

    }
}