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

        val email = intent.getStringExtra("email")

        val nicknameInput = findViewById<EditText>(R.id.nicknameEditText)
        val saveButton = findViewById<Button>(R.id.saveNicknameButton)

        saveButton.setOnClickListener {
            val nickname = nicknameInput.text.toString().trim()
            if (nickname.isNotEmpty() && email != null) {
                checkIfNicknameExists(nickname) { exists ->
                    if (exists) {
                        Toast.makeText(this, "이미 사용 중인 닉네임입니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        val userMap = hashMapOf(
                            "email" to email,
                            "nickname" to nickname,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        firestore.collection("Users").add(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "닉네임 저장 완료!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, UserPageActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "저장 실패", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            } else {
                Toast.makeText(this, "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkIfNicknameExists(nickname: String, callback: (Boolean) -> Unit) {
        val userRef = firestore.collection("Users")

        userRef.whereEqualTo("nickname", nickname).get()
            .addOnSuccessListener { querySnapshot ->
                val nicknameExists = !querySnapshot.isEmpty
                callback(nicknameExists)
            }
            .addOnFailureListener { e ->
                callback(false) // 오류 시 중복 없다고 처리
                Log.e("NicknameCheck", "닉네임 체크 오류", e)
            }
    }
}
