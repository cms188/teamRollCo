package com.example.test_2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private var generatedCode: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance() // Firebase 인증 인스턴스 초기화

        val btnLogin: Button = findViewById(R.id.btnLogin)
        val btnRegister: Button = findViewById(R.id.btnRegister)
        val btnFindPassword: Button = findViewById(R.id.btnFindPassword)
        val editEmail: EditText = findViewById(R.id.editEmail)
        val editPassword: EditText = findViewById(R.id.editPassword)
        val editFindEmail: EditText = findViewById(R.id.editFindEmail)
        val btnVerifyEmail: Button = findViewById(R.id.btnVerifyEmail)

        btnLogin.setOnClickListener {
            loginUser()
        }

        btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        btnFindPassword.setOnClickListener {
            editEmail.visibility = View.GONE
            editPassword.visibility = View.GONE
            btnLogin.visibility = View.GONE
            btnRegister.visibility = View.GONE
            btnFindPassword.visibility = View.GONE

            editFindEmail.visibility = View.VISIBLE
            btnVerifyEmail.visibility = View.VISIBLE
        }

        btnVerifyEmail.setOnClickListener {
            val email = editFindEmail.text.toString()

            checkIfEmailExists(email) { exists ->
                if (exists) {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "비밀번호 재설정 이메일을 보냈습니다.", Toast.LENGTH_SHORT).show()
                                editFindEmail.visibility = View.GONE
                                btnVerifyEmail.visibility = View.GONE

                                editEmail.visibility = View.VISIBLE
                                editEmail.setText(email)
                                editPassword.visibility = View.VISIBLE
                                btnLogin.visibility = View.VISIBLE
                                btnRegister.visibility = View.VISIBLE
                                btnFindPassword.visibility = View.VISIBLE
                            } else {
                                Toast.makeText(this, "오류 : ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "등록되지 않은 이메일입니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loginUser() {
        val email = findViewById<EditText>(R.id.editEmail).text.toString()
        val password = findViewById<EditText>(R.id.editPassword).text.toString()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "로그인에 성공했습니다.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "아이디 또는 비밀번호가 틀렸습니다.", Toast.LENGTH_SHORT).show()
                }
            }
    }


    private fun checkIfEmailExists(email: String, callback: (Boolean) -> Unit) {
        val userRef = FirebaseFirestore.getInstance().collection("Users")

        userRef.whereEqualTo("email", email).get()
            .addOnSuccessListener { querySnapshot ->
                val emailExists = !querySnapshot.isEmpty
                callback(emailExists)
            }
            .addOnFailureListener { e ->
                callback(false) // 오류 시 기본적으로 존재하지 않는 것으로 처리
                Log.e("EmailCheck", "이메일 체크 오류", e)
            }
    }
}
