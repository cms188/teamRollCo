package com.example.recipe_pocket

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import android.app.AlertDialog
import android.util.Log
import android.widget.ImageButton

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var currentPasswordSection: LinearLayout
    private lateinit var passwordChangeSection: LinearLayout
    private lateinit var currentPasswordEditText: TextInputEditText
    private lateinit var currentPasswordInputLayout: TextInputLayout
    private lateinit var newPasswordEditText: TextInputEditText
    private lateinit var newPasswordInputLayout: TextInputLayout
    private lateinit var confirmPasswordEditText: TextInputEditText
    private lateinit var confirmPasswordInputLayout: TextInputLayout
    private lateinit var passwordStrengthIndicator: LinearLayout
    private lateinit var strengthText: TextView
    private lateinit var strengthBar1: View
    private lateinit var strengthBar2: View
    private lateinit var strengthBar3: View
    private lateinit var btnVerifyCurrentPassword: MaterialButton
    private lateinit var btnChangePassword: MaterialButton
    private lateinit var btnFindPassword: TextView
    private lateinit var btnback: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_password)

        // Firebase Auth 초기화
        auth = FirebaseAuth.getInstance()

        // 툴바 표시 텍스트
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        toolbarTitle.text = "비밀번호 변경"

        // 툴바 상태바 높이만큼 보정
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams { height = statusBarHeight + dpToPx(56) }
            WindowInsetsCompat.CONSUMED
        }

        // 뷰 초기화
        initViews()
        // 클릭 이벤트 설정
        setupClickListeners()
        // 텍스트 변경 리스너 설정
        setupTextWatchers()
    }

    private fun initViews() {
        currentPasswordSection = findViewById(R.id.currentPasswordSection)
        passwordChangeSection = findViewById(R.id.passwordChangeSection)
        currentPasswordEditText = findViewById(R.id.currentPasswordEditText)
        currentPasswordInputLayout = findViewById(R.id.currentPasswordInputLayout)
        newPasswordEditText = findViewById(R.id.newPasswordEditText)
        newPasswordInputLayout = findViewById(R.id.newPasswordInputLayout)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout)
        passwordStrengthIndicator = findViewById(R.id.passwordStrengthIndicator)
        strengthText = findViewById(R.id.strengthText)
        strengthBar1 = findViewById(R.id.strengthBar1)
        strengthBar2 = findViewById(R.id.strengthBar2)
        strengthBar3 = findViewById(R.id.strengthBar3)
        btnVerifyCurrentPassword = findViewById(R.id.btnVerifyCurrentPassword)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnFindPassword = findViewById(R.id.btnFindPassword)
        btnback = findViewById(R.id.back_button)
    }

    private fun setupClickListeners() {
        // 현재 비밀번호 확인 버튼 클릭
        btnVerifyCurrentPassword.setOnClickListener {
            onVerifyCurrentPassword()
        }

        // 비밀번호 변경 버튼 클릭
        btnChangePassword.setOnClickListener {
            onChangePassword()
        }

        // 비밀번호 찾기 버튼 클릭
        btnFindPassword.setOnClickListener {
            onFindPassword()
        }

        // 뒤로가기 버튼 클릭
        btnback.setOnClickListener {
            finish()
        }
    }

    private fun setupTextWatchers() {
        // 새 비밀번호 입력 시 강도 체크
        newPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkPasswordStrength(s.toString())
                validatePasswords()
            }
        })

        // 비밀번호 확인 입력 시 일치 여부 체크
        confirmPasswordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePasswords()
            }
        })
    }

    // 현재 비밀번호 확인 - Firebase 재인증
    private fun onVerifyCurrentPassword() {
        val currentPassword = currentPasswordEditText.text.toString()

        if (currentPassword.isEmpty()) {
            currentPasswordInputLayout.error = "현재 비밀번호를 입력해주세요"
            return
        }

        val user = auth.currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "사용자 정보를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        // 프로그레스 표시
        btnVerifyCurrentPassword.isEnabled = false
        btnVerifyCurrentPassword.text = "확인 중..."

        // Firebase 재인증
        val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)

        user.reauthenticate(credential)
            .addOnSuccessListener {
                // 재인증 성공
                btnVerifyCurrentPassword.isEnabled = true
                btnVerifyCurrentPassword.text = "비밀번호 확인"
                currentPasswordInputLayout.error = null
                showPasswordChangeSection()
            }
            .addOnFailureListener { e ->
                // 재인증 실패
                btnVerifyCurrentPassword.isEnabled = true
                btnVerifyCurrentPassword.text = "비밀번호 확인"

                when (e) {
                    is FirebaseAuthInvalidCredentialsException -> {
                        currentPasswordInputLayout.error = "비밀번호가 일치하지 않습니다"
                    }
                    else -> {
                        currentPasswordInputLayout.error = "인증 실패: ${e.message}"
                    }
                }
                Log.e("ChangePassword", "재인증 실패", e)
            }
    }

    // 새 비밀번호 섹션 표시
    private fun showPasswordChangeSection() {
        passwordChangeSection.visibility = View.VISIBLE
        passwordStrengthIndicator.visibility = View.VISIBLE
        btnChangePassword.visibility = View.VISIBLE
        currentPasswordSection.visibility = View.GONE

        // 스크롤뷰를 아래로 이동
        findViewById<View>(R.id.passwordChangeSection)?.post {
            findViewById<View>(R.id.passwordChangeSection)?.parent?.requestChildFocus(
                findViewById(R.id.passwordChangeSection),
                findViewById(R.id.passwordChangeSection)
            )
        }
    }

    // 비밀번호 강도 체크
    private fun checkPasswordStrength(password: String) {
        val strength = calculatePasswordStrength(password)
        updatePasswordStrengthUI(strength)
    }

    // 비밀번호 강도 계산
    private fun calculatePasswordStrength(password: String): Int {
        var strength = 0

        if (password.length >= 8) strength++
        if (password.any { it.isDigit() }) strength++
        if (password.any { it.isUpperCase() }) strength++
        if (password.any { !it.isLetterOrDigit() }) strength++

        return when {
            strength <= 1 -> 1 // 약함
            strength <= 2 -> 2 // 보통
            else -> 3 // 강함
        }
    }

    // 비밀번호 강도 UI 업데이트
    private fun updatePasswordStrengthUI(strength: Int) {
        when (strength) {
            1 -> {
                strengthBar1.setBackgroundColor(getColor(R.color.error))
                strengthBar2.setBackgroundColor(getColor(R.color.primary_divider))
                strengthBar3.setBackgroundColor(getColor(R.color.primary_divider))
                strengthText.text = "약함"
                strengthText.setTextColor(getColor(R.color.error))
            }

            2 -> {
                strengthBar1.setBackgroundColor(getColor(R.color.warning))
                strengthBar2.setBackgroundColor(getColor(R.color.warning))
                strengthBar3.setBackgroundColor(getColor(R.color.primary_divider))
                strengthText.text = "보통"
                strengthText.setTextColor(getColor(R.color.warning))
            }

            3 -> {
                strengthBar1.setBackgroundColor(getColor(R.color.success))
                strengthBar2.setBackgroundColor(getColor(R.color.success))
                strengthBar3.setBackgroundColor(getColor(R.color.success))
                strengthText.text = "강함"
                strengthText.setTextColor(getColor(R.color.success))
            }
        }
    }

    // 비밀번호 일치 확인
    private fun validatePasswords() {
        val newPassword = newPasswordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        if (confirmPassword.isNotEmpty()) {
            if (newPassword != confirmPassword) {
                confirmPasswordInputLayout.error = "비밀번호가 일치하지 않습니다"
            } else {
                confirmPasswordInputLayout.error = null
            }
        }

        // 새 비밀번호 유효성 검사
        if (newPassword.isNotEmpty() && newPassword.length < 6) {
            newPasswordInputLayout.error = "비밀번호는 6자 이상이어야 합니다"
        } else {
            newPasswordInputLayout.error = null
        }
    }

    // 비밀번호 변경 - Firebase
    private fun onChangePassword() {
        val newPassword = newPasswordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val currentPassword = currentPasswordEditText.text.toString()

        // 입력 검증
        if (newPassword.isEmpty()) {
            newPasswordInputLayout.error = "새 비밀번호를 입력해주세요"
            return
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordInputLayout.error = "비밀번호를 다시 한번 입력해주세요"
            return
        }

        if (newPassword != confirmPassword) {
            confirmPasswordInputLayout.error = "비밀번호가 일치하지 않습니다"
            return
        }

        if (newPassword.length < 6) {
            newPasswordInputLayout.error = "비밀번호는 6자 이상이어야 합니다"
            return
        }

        // 현재 비밀번호와 새 비밀번호가 같은지 확인
        if (currentPassword == newPassword) {
            newPasswordInputLayout.error = "현재 비밀번호와 다른 비밀번호를 입력해주세요"
            return
        }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "사용자 정보를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        // 프로그레스 표시
        btnChangePassword.isEnabled = false
        btnChangePassword.text = "변경 중..."

        // Firebase 비밀번호 업데이트
        user.updatePassword(newPassword)
            .addOnSuccessListener {
                Toast.makeText(this, "비밀번호가 성공적으로 변경되었습니다", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                btnChangePassword.isEnabled = true
                btnChangePassword.text = "비밀번호 변경"

                Toast.makeText(this, "비밀번호 변경 실패: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("ChangePassword", "비밀번호 변경 실패", e)
            }
    }

    // 비밀번호 찾기 - 재설정 이메일 발송
    private fun onFindPassword() {
        val userEmail = auth.currentUser?.email
        if (userEmail == null) {
            Toast.makeText(this, "이메일 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("비밀번호 변경")
            .setMessage("$userEmail 주소로 비밀번호 재설정 링크를 보내시겠습니까?")
            .setPositiveButton("보내기") { _, _ ->
                auth.sendPasswordResetEmail(userEmail)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "이메일을 발송했습니다. 메일함을 확인해주세요.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "이메일 발송에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            Log.e("PasswordReset", "발송 실패", task.exception)
                        }
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}