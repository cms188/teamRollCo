package com.example.recipe_pocket.ui.auth

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
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
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.NidOAuthLogin
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.navercorp.nid.profile.NidProfileCallback
import com.navercorp.nid.profile.data.NidProfileResponse
import com.kakao.sdk.common.util.Utility
import com.google.android.material.button.MaterialButton
import utils.ToolbarUtils

class LoginActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private val RC_SIGN_IN = 1001
    private val naverLoginCallback = object : OAuthLoginCallback {
        override fun onSuccess() {
            runOnUiThread { requestNaverUserProfile() }
        }

        override fun onFailure(httpStatus: Int, message: String) {
            runOnUiThread {
                val errorCode = NaverIdLoginSDK.getLastErrorCode().code
                val errorDescription = NaverIdLoginSDK.getLastErrorDescription()
                val detail = "$httpStatus $message (" + errorCode + ": " + (errorDescription ?: "") + ")"
                Toast.makeText(this@LoginActivity, "Naver login failed: $detail", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onError(errorCode: Int, message: String) {
            onFailure(errorCode, message)
        }
    }

    // 모달 관련 뷰들
    private lateinit var modalOverlay: LinearLayout
    private lateinit var editFindEmail: EditText
    private lateinit var btnVerifyEmail: MaterialButton
    private lateinit var btnCancelFind: MaterialButton
    private var findpass = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        Log.d("KAKAO", "android_key_hash = ${Utility.getKeyHash(this)}")
        //https://developer.android.com/develop/ui/views/layout/edge-to-edge?hl=ko#kotlin
        //동작 모드 또는 버튼 모드에서 시각적 겹침을 방지
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
        val backButton = findViewById<ImageView>(R.id.back_button)
        val editFindEmail: EditText = findViewById(R.id.editFindEmail)
        val btnVerifyEmail: Button = findViewById(R.id.btnVerifyEmail)
        val btnFindPassword: TextView = findViewById(R.id.find_Password)

        val editEmail = findViewById<EditText>(R.id.editEmail)
        val editPassword = findViewById<EditText>(R.id.editPassword)

        val ivGoogleLogin = findViewById<ImageView>(R.id.iv_google_login_linear)
        val ivNaverLogin = findViewById<ImageView>(R.id.iv_naver_login_linear)

        loginButton.setOnClickListener {
            loginUser()
        }

        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        findViewById<ImageView>(R.id.iv_kakao_login_linear).setOnClickListener {
            kakaoLogin()
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

        ivNaverLogin.setOnClickListener {
            startNaverLogin()
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
                    updateFcmToken() // ★★★ FCM 토큰 저장 로직 호출 ★★★
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        FirebaseFirestore.getInstance()
                            .collection("Users")
                            .document(currentUser.uid)
                            .update("loginType", "email")
                    }
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
                    updateFcmToken() // ★★★ FCM 토큰 저장 로직 호출 ★★★
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
        val currentUser = auth.currentUser ?: return

        val userDocRef = firestore.collection("Users").document(currentUser.uid)

        userDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userDocRef.update("loginType", "google")
                        .addOnSuccessListener {
                            Log.d("GOOGLE_LOGIN", "loginType updated to google")
                        }

                    val nickname = document.getString("nickname")
                    if (!nickname.isNullOrEmpty()) {
                        Toast.makeText(this, "${nickname}님, 로그인 성공", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    } else {
                        navigateToNicknameSetup(email, "google")
                    }
                } else {
                    navigateToNicknameSetup(email, "google")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "사용자 정보 조회 실패", e)
                Toast.makeText(this, "사용자 정보 조회 실패", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToNicknameSetup(email: String, loginType: String = "google") {
        val intent = Intent(this, NicknameSetupActivity::class.java)
        intent.putExtra("email", email)
        intent.putExtra("loginType", loginType)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    // FCM 토큰을 가져와 Firestore에 저장하는 함수
    private fun updateFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM 토큰 가져오기 실패", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d(TAG, "현재 FCM 토큰: $token")
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val userDocRef = FirebaseFirestore.getInstance().collection("Users").document(currentUser.uid)
                // fcmTokens 필드에 현재 토큰을 배열 형태로 추가 (중복 방지)
                userDocRef.update("fcmTokens", FieldValue.arrayUnion(token))
            }
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////
    /*---------------------------카카오톡--------------------------*/

    private fun kakaoLogin() {
        // 카카오톡 설치 여부 확인
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            // 카카오톡으로 로그인
            loginWithKakaoTalk()
        } else {
            // 카카오 계정으로 로그인
            loginWithKakaoAccount()
        }
    }

    private fun loginWithKakaoTalk() {
        UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
            if (error != null) {
                // 사용자가 취소
                if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                    return@loginWithKakaoTalk
                }
                // 카카오톡 로그인 실패 시 카카오 계정으로 로그인
                loginWithKakaoAccount()
            } else if (token != null) {
                // 로그인 성공
                firebaseAuthWithKakao(token)
            }
        }
    }

    private fun loginWithKakaoAccount() {
        UserApiClient.instance.loginWithKakaoAccount(this) { token, error ->
            if (error != null) {
                Toast.makeText(this, "카카오 로그인 실패", Toast.LENGTH_SHORT).show()
            } else if (token != null) {
                // 로그인 성공
                firebaseAuthWithKakao(token)
            }
        }
    }

    private fun firebaseAuthWithKakao(token: OAuthToken) {
        // 카카오 사용자 정보 가져오기
        UserApiClient.instance.me { user, error ->
            if (error != null) {
                Toast.makeText(this, "사용자 정보 요청 실패", Toast.LENGTH_SHORT).show()
            } else if (user != null) {
                val kakaoEmail = user.kakaoAccount?.email
                val kakaoId = user.id.toString()

                if (kakaoEmail != null) {
                    // 카카오 ID를 비밀번호로 사용 (보안상 실제 서비스에서는 다른 방법 권장)
                    val password = "kakao_$kakaoId"

                    // 기존 사용자인지 확인 후 로그인 시도
                    auth.signInWithEmailAndPassword(kakaoEmail, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // 로그인 성공
                                Toast.makeText(this, "카카오 로그인 성공", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            } else {
                                // 신규 사용자 - 회원가입 진행
                                createKakaoFirebaseUser(
                                    kakaoEmail,
                                    password,
                                    kakaoId,
                                    user.kakaoAccount?.profile?.nickname
                                )
                            }
                        }
                } else {
                    Toast.makeText(this, "카카오 계정 이메일 동의가 필요합니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createKakaoFirebaseUser(email: String, password: String, kakaoId: String, nickname: String?) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Firestore에 사용자 정보 저장
                    val userData = hashMapOf(
                        "email" to email,
                        "nickname" to (nickname ?: "카카오유저"),
                        "kakaoId" to kakaoId,
                        "loginType" to "kakao",
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    FirebaseFirestore.getInstance()
                        .collection("Users")
                        .document(auth.currentUser!!.uid)
                        .set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "카카오 회원가입 성공", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                } else {
                    Toast.makeText(this, "회원가입 실패: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    /////////////////////////////Naver login api/////////////////////////////
    private fun startNaverLogin() {
        NaverIdLoginSDK.authenticate(this, naverLoginCallback)
    }

    private fun requestNaverUserProfile() {
        NidOAuthLogin().callProfileApi(object : NidProfileCallback<NidProfileResponse> {
            override fun onSuccess(result: NidProfileResponse) {
                runOnUiThread { handleNaverProfile(result) }
            }

            override fun onFailure(httpStatus: Int, message: String) {
                runOnUiThread { handleNaverProfileError(httpStatus, message) }
            }

            override fun onError(errorCode: Int, message: String) {
                runOnUiThread { handleNaverProfileError(errorCode, message) }
            }
        })
    }

    private fun handleNaverProfile(result: NidProfileResponse) {
        val profile = result.profile
        val naverId = profile?.id
        val email = profile?.email
        if (naverId.isNullOrBlank()) {
            Toast.makeText(this, "Failed to get Naver user id.", Toast.LENGTH_SHORT).show()
            return
        }
        if (email.isNullOrBlank()) {
            Toast.makeText(this, "Email permission is required for Naver login.", Toast.LENGTH_SHORT).show()
            return
        }
        val nickname = profile.nickname
        val profileImageUrl = profile.profileImage
        val password = "naver_$naverId"
        signInWithNaver(email, password, nickname, profileImageUrl)
    }

    private fun handleNaverProfileError(code: Int, message: String) {
        val errorCode = NaverIdLoginSDK.getLastErrorCode().code
        val errorDescription = NaverIdLoginSDK.getLastErrorDescription()
        val detail = "$code $message (" + errorCode + ": " + (errorDescription ?: "") + ")"
        Toast.makeText(this, "Naver profile error: $detail", Toast.LENGTH_SHORT).show()
    }

    private fun signInWithNaver(email: String, password: String, nickname: String?, profileImageUrl: String?) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    updateFcmToken()
                    val user = auth.currentUser
                    if (user != null) {
                        val updates = mutableMapOf<String, Any>(
                            "email" to email,
                            "loginType" to "naver"
                        )
                        nickname?.let { updates["nickname"] = it }
                        profileImageUrl?.let { updates["profileImageUrl"] = it }
                        FirebaseFirestore.getInstance()
                            .collection("Users")
                            .document(user.uid)
                            .set(updates, SetOptions.merge())
                            .addOnSuccessListener { navigateToMainAfterNaverLogin() }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to update Naver user", e)
                                navigateToMainAfterNaverLogin()
                            }
                    } else {
                        Toast.makeText(this, "Login state is unknown.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    createNaverFirebaseUser(email, password, nickname, profileImageUrl)
                }
            }
    }

    private fun createNaverFirebaseUser(email: String, password: String, nickname: String?, profileImageUrl: String?) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val userData = hashMapOf<String, Any>(
                            "email" to email,
                            "loginType" to "naver",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        nickname?.let { userData["nickname"] = it }
                        profileImageUrl?.let { userData["profileImageUrl"] = it }
                        FirebaseFirestore.getInstance()
                            .collection("Users")
                            .document(user.uid)
                            .set(userData)
                            .addOnSuccessListener {
                                updateFcmToken()
                                navigateToMainAfterNaverLogin()
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to save Naver user", e)
                                Toast.makeText(this, "Could not save user info.", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Login state is unknown.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorMessage = task.exception?.message ?: "unknown error"
                    Toast.makeText(this, "Naver sign-up failed: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToMainAfterNaverLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}