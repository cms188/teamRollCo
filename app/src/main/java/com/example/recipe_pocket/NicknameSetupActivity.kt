package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class NicknameSetupActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // 이 액티비티가 어떤 모드로 실행되었는지 구분하기 위한 변수
    private var currentMode: String = MODE_SETUP

    // companion object를 사용해 모드를 나타내는 상수를 정의 (오타 방지)
    companion object {
        const val EXTRA_MODE = "ACTIVITY_MODE"
        const val MODE_SETUP = "MODE_SETUP" // 초기 닉네임 설정 모드
        const val MODE_UPDATE = "MODE_UPDATE" // 닉네임 변경 모드
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nickname_setup)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Intent로부터 실행 모드를 받아오고 전달된 값이 없으면 기본값(MODE_SETUP) 사용
        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SETUP

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // UI 요소들 연결
        val nicknameTitle = findViewById<TextView>(R.id.nicknameTitle)
        val nicknameInput = findViewById<EditText>(R.id.nicknameEditText)
        val saveButton = findViewById<Button>(R.id.saveNicknameButton)

        // 모드에 따라 UI 텍스트 변경
        if (currentMode == MODE_UPDATE) {
            nicknameTitle.text = "변경할 닉네임을 입력하세요"
            saveButton.text = "저장하기"
        } else { // MODE_SETUP
            nicknameTitle.text = "사용할 닉네임을 입력해주세요"
            saveButton.text = "가입하기"
        }


        saveButton.setOnClickListener {
            val nickname = nicknameInput.text.toString().trim()
            val userUid = currentUser.uid

            if (nickname.isNotEmpty()) {
                checkIfNicknameExists(nickname) { exists ->
                    if (exists) {
                        Toast.makeText(this, "이미 사용 중인 닉네임입니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        // 모드에 따라 Firestore 저장 로직 분기
                        if (currentMode == MODE_UPDATE) {
                            updateUserNickname(userUid, nickname)
                        } else { // MODE_SETUP
                            setupNewUser(userUid, currentUser.email, nickname)
                        }
                    }
                }
            } else {
                Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 신규 사용자 닉네임 설정 로직
    private fun setupNewUser(uid: String, email: String?, nickname: String) {
        val userMap = hashMapOf(
            "email" to email,
            "nickname" to nickname,
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("Users").document(uid).set(userMap)
            .addOnSuccessListener {
                Toast.makeText(this, "닉네임 저장 완료!", Toast.LENGTH_SHORT).show()
                // 설정 완료 후 메인 화면으로 이동
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("NicknameSetup", "신규 닉네임 저장 실패", e)
            }
    }

    // 기존 사용자 닉네임 변경 로직
    private fun updateUserNickname(uid: String, nickname: String) {
        firestore.collection("Users").document(uid).update("nickname", nickname)
            .addOnSuccessListener {
                Toast.makeText(this, "닉네임이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                // 변경 완료 후 이전 화면(UserPageActivity)으로 돌아가기 위해 현재 액티비티를 종료
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "변경 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("NicknameSetup", "닉네임 업데이트 실패", e)
            }
    }

    // 닉네임 중복 확인 함수
    private fun checkIfNicknameExists(nickname: String, callback: (Boolean) -> Unit) {
        firestore.collection("Users").whereEqualTo("nickname", nickname).get()
            .addOnSuccessListener { querySnapshot ->
                callback(!querySnapshot.isEmpty)
            }
            .addOnFailureListener { e ->
                callback(false)
                Log.e("NicknameCheck", "닉네임 중복 체크 오류", e)
            }
    }
}