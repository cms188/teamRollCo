package com.example.recipe_pocket.ui.auth

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.recipe_pocket.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.example.recipe_pocket.ui.main.MainActivity
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton

class LoginActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private val RC_SIGN_IN = 1001

    // 모달 관련 뷰들
    private lateinit var modalOverlay: LinearLayout
    private lateinit var editFindEmail: EditText
    private lateinit var btnVerifyEmail: MaterialButton
    private lateinit var btnCancelFind: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_login_linear_layout)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                topMargin = insets.top
            }
            WindowInsetsCompat.CONSUMED
        }

        // Firebase 인증 인스턴스 초기화
        auth = FirebaseAuth.getInstance()

        // 툴바 설정
        utils.ToolbarUtils.setupTransparentToolbar(this, "", navigateToMainActivity = true)

        // 뷰 초기화
        initViews()

        // 리스너 설정
        setupListeners()

        // Google 로그인 설정
        setupGoogleSignIn()
    }

    private fun initViews() {
        // 모달 관련 뷰들
        modalOverlay = findViewById(R.id.modal_overlay)
        editFindEmail = findViewById(R.id.editFindEmail)
        btnVerifyEmail = findViewById(R.id.btnVerifyEmail)
        btnCancelFind = findViewById(R.id.btnCancelFind)
    }

    private fun setupListeners() {
        val loginButton: MaterialButton = findViewById(R.id.btnLogin)
        val registerButton: TextView = findViewById(R.id.btnRegister)
        val btnFindPassword: TextView = findViewById(R.id.find_Password)
        val ivGoogleLogin = findViewById<ImageView>(R.id.iv_google_login_linear)

        // 로그인 버튼
        loginButton.setOnClickListener {
            loginUser()
        }

        // 회원가입 버튼
        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // 비밀번호 찾기 버튼 - 모달 열기
        btnFindPassword.setOnClickListener {
            showFindPasswordModal()
        }

        // 모달 취소 버튼
        btnCancelFind.setOnClickListener {
            hideFindPasswordModal()
        }

        // 모달 오버레이 클릭 시 닫기
        modalOverlay.setOnClickListener {
            hideFindPasswordModal()
        }

        // 모달 컨테이너 클릭 시 이벤트 전파 방지
        findViewById<LinearLayout>(R.id.FindPasswordContainer).setOnClickListener { }

        // 이메일 인증 버튼
        btnVerifyEmail.setOnClickListener {
            val email = editFindEmail.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkIfEmailExists(email) { exists ->
                if (exists) {
                    sendPasswordResetEmail(email)
                } else {
                    Toast.makeText(this, "등록되지 않은 이메일입니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Google 로그인
        ivGoogleLogin.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    private fun showFindPasswordModal() {
        modalOverlay.visibility = View.VISIBLE
        editFindEmail.text.clear() // 입력 필드 초기화

        // 애니메이션 효과
        modalOverlay.alpha = 0f
        modalOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun hideFindPasswordModal() {
        modalOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                modalOverlay.visibility = View.GONE
                editFindEmail.text.clear() // 입력 내용 초기화
            }
            .start()
    }

    private fun sendPasswordResetEmail(email: String) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "비밀번호 재설정 이메일을 보냈습니다.", Toast.LENGTH_SHORT).show()
                    hideFindPasswordModal()

                    // 이메일 필드에 이메일 미리 입력
                    val emailField = findViewById<EditText>(R.id.editEmail)
                    emailField.setText(email)
                } else {
                    Toast.makeText(this, "오류 : ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))  // res/values/strings.xml에 있음
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun loginUser() {
        val emailEditText = findViewById<EditText>(R.id.editEmail)
        val passwordEditText = findViewById<EditText>(R.id.editPassword)

        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        // --- 중요: 입력값 유효성 검사 ---
        if (email.isEmpty()) {
            Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
            emailEditText.requestFocus()
            return
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            passwordEditText.requestFocus()
            return
        }
        // --- 유효성 검사 끝 ---

        // 디버깅을 위해 이메일 로그 추가 (민감 정보 주의)
        Log.d(TAG, "Attempting to sign in with email: $email")

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithEmail:success")
                    Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    // 로그인 성공 후에는 보통 이전 액티비티 스택을 모두 지우고 새 태스크로 시작합니다.
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
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
                    Log.e(
                        "GOOGLE_LOGIN",
                        "🔴 Error 10: SHA-1 fingerprint missing or misconfigured in Firebase!"
                    )
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
                    Toast.makeText(this, "로그인 실패: ${task.exception?.message}", Toast.LENGTH_SHORT)
                        .show()
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