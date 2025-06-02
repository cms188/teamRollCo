package com.example.recipe_pocket

import android.content.ContentValues.TAG
import android.content.Intent
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.activity.result.contract.ActivityResultContracts
import com.example.recipe_pocket.databinding.ActivityLoginBinding
import com.example.recipe_pocket.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ApiException


class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private val RC_SIGN_IN = 1001

    var findpass: Boolean = false //뒤로가기 버튼 두가지 기능을 위한 Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        //https://developer.android.com/develop/ui/views/layout/edge-to-edge?hl=ko#kotlin
        //동작 모드 또는 버튼 모드에서 시각적 겹침을 방지
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_login_linear_layout)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                topMargin = insets.top //상단도 마찬가지로 겹침 방지. 꼭 필요한 것은 아님
            }
            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
        auth = FirebaseAuth.getInstance() // Firebase 인증 인스턴스 초기화

        val loginButton: Button = findViewById(R.id.btnLogin)
        val registerButton: TextView = findViewById(R.id.btnRegister)
        val backButton: ImageView = findViewById(R.id.iv_back_button_login)
        val editFindEmail: EditText = findViewById(R.id.editFindEmail)
        val btnVerifyEmail: Button = findViewById(R.id.btnVerifyEmail)
        val btnFindPassword: TextView = findViewById(R.id.find_Password)

        val editEmail = findViewById<EditText>(R.id.editEmail)
        val editPassword = findViewById<EditText>(R.id.editPassword)

        val ivGoogleLogin = findViewById<ImageView>(R.id.iv_google_login_linear)

        loginButton.setOnClickListener {
            loginUser()
        }

        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        backButton.setOnClickListener {
            if (findpass) {
                editEmail.visibility = View.VISIBLE
                editPassword.visibility = View.VISIBLE
                loginButton.visibility = View.VISIBLE
                registerButton.visibility = View.VISIBLE
                btnFindPassword.visibility = View.VISIBLE

                editFindEmail.visibility = View.GONE
                btnVerifyEmail.visibility = View.GONE
                findpass = false
            }
            else {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
            }
        }

        btnFindPassword.setOnClickListener {
            editEmail.visibility = View.GONE
            editPassword.visibility = View.GONE
            loginButton.visibility = View.GONE
            registerButton.visibility = View.GONE
            btnFindPassword.visibility = View.GONE

            editFindEmail.visibility = View.VISIBLE
            btnVerifyEmail.visibility = View.VISIBLE
            findpass = true
        }

        btnVerifyEmail.setOnClickListener {
            val email = editFindEmail.text.toString().trim()

            checkIfEmailExists(email) { exists ->
                if (exists) {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "비밀번호 재설정 이메일을 보냈습니다.", Toast.LENGTH_SHORT)
                                    .show()
                                editFindEmail.visibility = View.GONE
                                btnVerifyEmail.visibility = View.GONE

                                editEmail.visibility = View.VISIBLE
                                editEmail.setText(email)
                                editPassword.visibility = View.VISIBLE
                                loginButton.visibility = View.VISIBLE
                                registerButton.visibility = View.VISIBLE
                                btnFindPassword.visibility = View.VISIBLE
                            } else {
                                Toast.makeText(
                                    this,
                                    "오류 : ${task.exception?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "등록되지 않은 이메일입니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // res/values/strings.xml에 있음
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        ivGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
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




    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("GOOGLE_LOGIN", "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.e("GOOGLE_LOGIN", "Google sign in failed, statusCode=${e.statusCode}", e)
                if (e.statusCode == 10) {
                    Log.e("GOOGLE_LOGIN", "🔴 Error 10: SHA-1 fingerprint missing or misconfigured in Firebase!")
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("GOOGLE_LOGIN", "✅ signInWithCredential:success")
                    val user = auth.currentUser

                    if (user != null) {
                        val email = user.email
                        if (email != null) {
                            checkIfUserHasNickname(email)
                        } else {
                            Toast.makeText(this, "이메일 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "로그인된 사용자를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("GOOGLE_LOGIN", "❌ signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "로그인 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkIfUserHasNickname(email: String) {
        val firestore = FirebaseFirestore.getInstance()
        val userRef = firestore.collection("Users").whereEqualTo("email", email)

        userRef.get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val nickname = document.getString("nickname")

                    if (!nickname.isNullOrEmpty()) {
                        Toast.makeText(this, "${nickname}님, 로그인 성공", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } else {
                        navigateToNicknameSetup(email)
                    }
                } else {
                    // 사용자 문서가 없을 경우에도 닉네임 설정 액티비티로 이동
                    Log.w("Firestore", "사용자 문서 없음")
                    navigateToNicknameSetup(email)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "사용자 닉네임 조회 실패", e)
                Toast.makeText(this, "사용자 정보 조회 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToNicknameSetup(email: String) {
        val intent = Intent(this, NicknameSetupActivity::class.java)
        intent.putExtra("email", email)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
