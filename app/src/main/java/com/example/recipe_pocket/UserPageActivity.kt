package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserPageActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var greetingTextView: TextView // 닉네임을 표시할 TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_userpage)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        greetingTextView = findViewById(R.id.greetingTextView) // 레이아웃에서 TextView 연결

        val currentUser = auth.currentUser // 로그인된 사용자 가져오기
        if (currentUser != null) {
            val email = currentUser.email // 현재 로그인된 사용자의 이메일로 사용자 정보 가져오기
            if (email != null) {
                getUserNicknameByEmail(email) // 이메일을 사용해 Firestore에서 닉네임 조회
            } else {
                Toast.makeText(this, "로그인 상태를 확인할 수 없습니다.", Toast.LENGTH_SHORT).show()
                navigateToLoginActivity()
            }
        } else {
            // 로그인 안 된 상태일 경우
            Toast.makeText(this, "로그인 상태가 아닙니다.", Toast.LENGTH_SHORT).show()
            navigateToLoginActivity()
        }

        // 로그아웃 버튼 처리
        val logoutButton: Button = findViewById(R.id.btnLogout)
        logoutButton.setOnClickListener {
            logoutUser()
        }
    }

    private fun getUserNicknameByEmail(email: String) {
        val userRef = firestore.collection("Users")
            .whereEqualTo("email", email)

        userRef.get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val nickname = document.getString("nickname")

                    runOnUiThread {
                        if (!nickname.isNullOrEmpty()) {
                            greetingTextView.text = "${nickname}님, 환영합니다."
                            Log.d("Firestore", "닉네임 로드 성공: $nickname")
                        } else {
                            greetingTextView.text = "회원님, 환영합니다."
                            Log.w("Firestore", "닉네임 필드가 비어있음")
                        }
                    }
                } else {
                    // 닉네임 없음 → 닉네임 생성 액티비티로 이동
                    Log.w("Firestore", "사용자 문서 없음, 닉네임 생성으로 이동")
                    navigateToNicknameSetup(email)
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    greetingTextView.text = "환영합니다."
                    Log.e("Firestore", "닉네임 조회 실패: ${e.message}")
                    Toast.makeText(this, "사용자 정보 조회 실패", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 닉네임 설정 액티비티로 이동
    private fun navigateToNicknameSetup(email: String) {
        val intent = Intent(this, NicknameSetupActivity::class.java)
        intent.putExtra("email", email)
        startActivity(intent)
        finish() // 현재 화면 닫기
    }

    // 로그아웃 처리
    private fun logoutUser() {
        auth.signOut()
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
        navigateToLoginActivity() // 로그아웃 후 로그인 화면으로 이동
    }

    // 로그인 화면으로 이동
    private fun navigateToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // 현재 UserPageActivity 종료
    }
}