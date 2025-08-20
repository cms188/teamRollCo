package com.example.recipe_pocket

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.imageview.ShapeableImageView

class EditProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_profile)

        // 툴바 표시 텍스트 설정
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = "프로필"

        // 툴바 상태바 높이만큼 보정
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams { height = statusBarHeight + dpToPx(56) }
            WindowInsetsCompat.CONSUMED
        }

        val imageView_currentProfile = findViewById<ShapeableImageView>(R.id.imageView_currentProfile)  // 프로필 이미지
        val btnChangeProfileImage = findViewById<LinearLayout>(R.id.btnChangeProfileImage)
        val btnChangeNickname = findViewById<LinearLayout>(R.id.btnChangeNickname)
        val btnChangePassword = findViewById<LinearLayout>(R.id.btnChangePassword)
        val btnLogout = findViewById<LinearLayout>(R.id.btnLogout)
        val btnDeleteAccount = findViewById<LinearLayout>(R.id.btnDeleteAccount)

        // 클릭 이벤트 리스너 설정
        setupClickListeners(
            imageView_currentProfile,
            btnChangeProfileImage,
            btnChangeNickname,
            btnChangePassword,
            btnLogout,
            btnDeleteAccount
        )
    }

    private fun setupClickListeners(
        imageView_currentProfile: ShapeableImageView,
        btnChangeProfileImage: LinearLayout,
        btnChangeNickname: LinearLayout,
        btnChangePassword: LinearLayout,
        btnLogout: LinearLayout,
        btnDeleteAccount: LinearLayout
    ) {
        // 프로필 이미지 클릭 (이미지 직접 클릭)
        imageView_currentProfile.setOnClickListener {
            onProfileImageClicked()
        }

        // 프로필 이미지 변경 클릭 (아이콘)
        btnChangeProfileImage.setOnClickListener {
            onProfileImageClicked()
        }

        // 닉네임 변경 클릭
        btnChangeNickname.setOnClickListener {
            onChangeNicknameClicked()
        }

        // 비밀번호 변경 클릭
        btnChangePassword.setOnClickListener {
            onChangePasswordClicked()
        }

        // 로그아웃 클릭
        btnLogout.setOnClickListener {
            onLogoutClicked()
        }

        // 회원탈퇴 클릭
        btnDeleteAccount.setOnClickListener {
            onDeleteAccountClicked()
        }
    }

    // 프로필 이미지 변경
    private fun onProfileImageClicked() {
        // TODO: 프로필 이미지 변경
        Toast.makeText(this, "프로필 이미지 변경", Toast.LENGTH_SHORT).show()
    }

    // 닉네임 변경
    private fun onChangeNicknameClicked() {
        // TODO: 닉네임 변경 화면으로 이동
        Toast.makeText(this, "닉네임 변경", Toast.LENGTH_SHORT).show()
    }

    // 비밀번호 변경
    private fun onChangePasswordClicked() {
        // TODO: 비밀번호 변경 화면으로 이동
        Toast.makeText(this, "비밀번호 변경", Toast.LENGTH_SHORT).show()
    }

    // 로그아웃
    private fun onLogoutClicked() {
        // TODO: 로그아웃 확인 다이얼로그 표시 후 로그아웃
        Toast.makeText(this, "로그아웃", Toast.LENGTH_SHORT).show()
    }

    // 회원탈퇴
    private fun onDeleteAccountClicked() {
        // TODO: 회원탈퇴 확인 다이얼로그 표시 후 탈퇴
        Toast.makeText(this, "회원탈퇴", Toast.LENGTH_SHORT).show()
    }


    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}