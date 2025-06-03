package com.example.recipe_pocket

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

class NicknameSetupActivity : AppCompatActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nickname_setup)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // intent로부터 email을 받는 대신, 현재 로그인된 사용자 정보에서 직접 가져오는 것이 더 안전하고 정확합니다.
        // val email = intent.getStringExtra("email") // 이 줄은 필요에 따라 유지하거나 삭제할 수 있습니다.
        val currentUser = auth.currentUser // 현재 로그인된 사용자를 가져옵니다.

        val nicknameInput = findViewById<EditText>(R.id.nicknameEditText)
        val saveButton = findViewById<Button>(R.id.saveNicknameButton)

        saveButton.setOnClickListener {
            val nickname = nicknameInput.text.toString().trim()

            // 현재 로그인된 사용자가 있는지 확인
            if (currentUser == null) {
                Toast.makeText(this, "사용자 정보를 가져올 수 없습니다. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
                // 예: 로그인 화면으로 이동
                startActivity(Intent(this, LoginActivity::class.java)) // LoginActivity가 실제 로그인 화면 클래스명인지 확인
                finish()
                return@setOnClickListener
            }

            val userUid = currentUser.uid // 현재 사용자의 고유 UID를 가져옵니다.
            val userEmail = currentUser.email // 현재 사용자의 이메일을 가져옵니다.

            if (nickname.isNotEmpty()) {
                checkIfNicknameExists(nickname) { exists ->
                    if (exists) {
                        Toast.makeText(this, "이미 사용 중인 닉네임입니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        val userMap = hashMapOf(
                            "email" to userEmail, // Firebase Auth에서 가져온 이메일 사용
                            "nickname" to nickname,
                            "createdAt" to FieldValue.serverTimestamp()
                            // 필요하다면 profileImageUrl 등 다른 정보도 여기서 초기화하거나 추가할 수 있습니다.
                            // 예: "profileImageUrl" to currentUser.photoUrl?.toString()
                        )

                        // Users 컬렉션에 문서를 추가할 때, 문서 ID를 사용자의 UID로 지정합니다.
                        // .add() 대신 .document(userUid).set(userMap) 을 사용합니다.
                        firestore.collection("Users").document(userUid).set(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "닉네임 저장 완료!", Toast.LENGTH_SHORT).show()
                                // 닉네임 설정 후 메인 화면 또는 사용자 페이지 등으로 이동
                                val intent = Intent(this, MainActivity::class.java) // 또는 UserPageActivity
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // 이전 액티비티 스택을 모두 지우고 새 화면 시작
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e("NicknameSetup", "닉네임 저장 실패", e)
                            }
                    }
                }
            } else {
                Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // checkIfNicknameExists 함수는 기존과 동일하게 사용 가능합니다.
    private fun checkIfNicknameExists(nickname: String, callback: (Boolean) -> Unit) {
        val userRef = firestore.collection("Users")

        userRef.whereEqualTo("nickname", nickname).get()
            .addOnSuccessListener { querySnapshot ->
                val nicknameExists = !querySnapshot.isEmpty
                callback(nicknameExists)
            }
            .addOnFailureListener { e ->
                callback(false) // 오류 시 중복 없다고 처리 (네트워크 오류 등)
                Log.e("NicknameCheck", "닉네임 중복 체크 오류", e)
            }
    }
}