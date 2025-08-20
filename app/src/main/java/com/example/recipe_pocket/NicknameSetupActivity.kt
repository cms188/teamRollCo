package com.example.recipe_pocket

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class NicknameSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_nickname_setup)

        // 툴바 표시 텍스트 설정
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = ""

        // 툴바 상태바 높이만큼 보정
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams { height = statusBarHeight + dpToPx(56) }
            WindowInsetsCompat.CONSUMED
        }

        /*
        // 닉네임 입력 도움말
        // 현재 사용자 닉네임 띄워주세요
        val nicknameInputLayout = findViewById<TextInputLayout>(R.id.nicknameInputLayout)
        nicknameInputLayout.hint = "현재닉네임"
        nicknameInputLayout.helperText = "사용할 수 있는 닉네임이에요."
        // 일반 텍스트 색
        nicknameInputLayout.setHelperTextColor(ContextCompat.getColorStateList(this, R.color.text_secondary))
        // 경고 텍스트 색
        nicknameInputLayout.setHelperTextColor(ContextCompat.getColorStateList(this, R.color.error_red))
        */

        /*
        // 저장 버튼
        // 닉네임에 변경사항이 생기면 보이게 해주세요
        // 초기 상태는 GONE 입니다
        val btnChangeNickname = findViewById<Toolbar>(R.id.btnChangeNickname)
        btnChangeNickname.visibility = View.GONE
        btnChangeNickname.visibility = View.VISIBLE
        */

        // 저장 버튼 클릭
        val btnSubmit = findViewById<MaterialButton>(R.id.btn_submit)
        btnSubmit.setOnClickListener {
            onSaveButtonClicked()
        }

    }


    // 저장 버튼 클릭
    private fun onSaveButtonClicked() {
        // 입력 내용
        val nicknameEditText = findViewById<TextInputEditText>(R.id.nicknameEditText)
        val enteredNickname = nicknameEditText.text?.toString()?.trim() ?: ""


        println("입력된 닉네임: $enteredNickname")

    }



    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}