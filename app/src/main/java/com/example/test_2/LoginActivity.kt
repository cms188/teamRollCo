package com.example.test_2

import android.content.ContentValues.TAG
import android.content.Intent
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance() // Firebase 인증 인스턴스 초기화

        val loginButton: Button = findViewById(R.id.btnLogin)
        val registerButton: TextView = findViewById(R.id.btnRegister)
        val backButton: ImageView = findViewById(R.id.iv_back_button)

        loginButton.setOnClickListener {
            loginUser()
        }

        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loginUser() {
        val emailEditText = findViewById<EditText>(R.id.editEmail)
        val passwordEditText = findViewById<EditText>(R.id.editPassword)

        val email = emailEditText.text.toString().trim() // .trim() 추가하여 앞뒤 공백 제거
        val password = passwordEditText.text.toString().trim() // .trim() 추가

        // --- 중요: 입력값 유효성 검사 ---
        if (email.isEmpty()) {
            // emailEditText.error = "이메일을 입력해주세요." // EditText에 직접 오류 표시
            Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            emailEditText.requestFocus() // 포커스를 이메일 필드로 이동
            return // 함수 종료
        }

        if (password.isEmpty()) {
            // passwordEditText.error = "비밀번호를 입력해주세요." // EditText에 직접 오류 표시
            Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            passwordEditText.requestFocus() // 포커스를 비밀번호 필드로 이동
            return // 함수 종료
        }
        // --- 유효성 검사 끝 ---

        Log.d(TAG, "Attempting to sign in with email: $email") // 디버깅을 위해 이메일 로그 추가 (민감 정보 주의)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    // 로그인 성공 후에는 보통 이전 액티비티 스택을 모두 지우고 새 태스크로 시작합니다.
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish() // LoginActivity 종료
                } else {
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    // 실패 원인에 따라 좀 더 구체적인 메시지를 보여줄 수도 있습니다.
                    // 예: task.exception 종류 확인 (FirebaseAuthInvalidUserException, FirebaseAuthInvalidCredentialsException 등)
                    Toast.makeText(this, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}