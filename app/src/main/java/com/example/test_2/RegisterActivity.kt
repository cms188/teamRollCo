package com.example.test_2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth //Firebase를 사용하는 권한
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance() //Firebase에서 인스턴스를 가져올 것이다!
        firestore = FirebaseFirestore.getInstance()

        val registerButton: Button = findViewById(R.id.btnRegister2) //회원가입 버튼 객체 생성

        registerButton.setOnClickListener { // 눌렀을 때 registerUser 함수를 쓸 것이다!
            registerUser()
        }
    }

    private fun registerUser() { //xml에 있는 id의 이름을 가져와서 객체로 생성
        val password = findViewById<EditText>(R.id.editPassword).text.toString()
        val password2 = findViewById<EditText>(R.id.editPassword2).text.toString() //비밀번호 재입력
        val email = findViewById<EditText>(R.id.editEmail).text.toString()
        val nickname = findViewById<EditText>(R.id.editNickName).text.toString()

        if (password != password2) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        checkIfEmailOrNicknameExists(email, nickname) { emailExists, nicknameExists ->
            when {
                emailExists -> {
                    Toast.makeText(this, "이메일이 이미 존재합니다.", Toast.LENGTH_SHORT).show()
                }

                nicknameExists -> {
                    Toast.makeText(this, "닉네임이 이미 존재합니다.", Toast.LENGTH_SHORT).show()
                }

                else -> {
                    // Firebase 인증을 사용하여 이메일과 비밀번호로 사용자 생성
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this) { task ->
                            if (task.isSuccessful) {
                                // Firestore에 사용자 정보 저장
                                saveUserData(email, nickname)

                                Toast.makeText(this, "회원가입 성공", Toast.LENGTH_SHORT).show()
                                navigateToLoginActivity() // 로그인 화면으로 이동
                            } else {
                                Toast.makeText(
                                    this,
                                    "회원가입 실패: ${task.exception?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                }
            }
        }
    }

    private fun checkIfEmailOrNicknameExists(email: String, nickname: String, callback: (Boolean, Boolean) -> Unit) {
        val userRef = firestore.collection("Users")

        // 이메일 중복 체크
        userRef.whereEqualTo("email", email).get()
            .addOnSuccessListener { emailQuerySnapshot ->
                val emailExists = !emailQuerySnapshot.isEmpty

                // 닉네임 중복 체크
                userRef.whereEqualTo("nickname", nickname).get()
                    .addOnSuccessListener { nicknameQuerySnapshot ->
                        val nicknameExists = !nicknameQuerySnapshot.isEmpty
                        callback(emailExists, nicknameExists)
                    }
                    .addOnFailureListener { e ->
                        callback(emailExists, false) // 닉네임 체크 오류 시 nicknameExists는 false로 처리
                        Log.e("RegisterActivity", "닉네임 체크 오류", e)
                    }
            }
            .addOnFailureListener { e ->
                callback(false, false) // 이메일 체크 오류 시 emailExists, nicknameExists 모두 false로 처리
                Log.e("RegisterActivity", "이메일 체크 오류", e)
            }
    }

    private fun saveUserData(email: String, nickname: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("RegisterActivity", "Firebase 사용자 인증 정보 없음")
            return
        }

        val userData = hashMapOf(
            "email" to email,
            "nickname" to nickname,
            "createdAt" to FieldValue.serverTimestamp()  // 생성 시간 추가
        )

        // 사용자 UID를 문서 ID로 명시적으로 지정
        firestore.collection("Users")
            .document(currentUser.uid)  // Firebase Auth의 UID 사용
            .set(userData)
            .addOnSuccessListener {
                Log.d("RegisterActivity", "사용자 문서 생성 성공: ${currentUser.uid}")
            }
            .addOnFailureListener { e ->
                Log.e("RegisterActivity", "문서 생성 실패", e)
                Toast.makeText(this, "사용자 정보 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java) // 로그인 화면으로 이동
        startActivity(intent)
        finish()  // 현재 액티비티를 종료하여 뒤로가기 버튼으로 다시 돌아오지 않도록
    }
}