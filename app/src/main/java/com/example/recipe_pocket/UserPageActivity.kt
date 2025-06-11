package com.example.recipe_pocket

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class UserPageActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var greetingTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_userpage)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // UI 요소 연결
        greetingTextView = findViewById(R.id.greetingTextView)
        val changeNicknameButton: Button = findViewById(R.id.btnChangeNickname)
        val changePasswordButton: Button = findViewById(R.id.btnChangePassword)
        val logoutButton: Button = findViewById(R.id.btnLogout)
        val deleteAccountButton: Button = findViewById(R.id.btnDeleteAccount)

        // 리스너 설정
        changeNicknameButton.setOnClickListener { handleChangeNickname() }
        changePasswordButton.setOnClickListener { handleChangePassword() }
        logoutButton.setOnClickListener { logoutUser() }
        deleteAccountButton.setOnClickListener { showDeleteAccountConfirmation() }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            navigateToLoginActivity()
            return
        }

        firestore.collection("Users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    greetingTextView.text = "${document.getString("nickname")}님, 환영합니다."
                } else {
                    navigateToNicknameSetup()
                }
            }
            .addOnFailureListener { e ->
                greetingTextView.text = "환영합니다."
                Log.e("Firestore", "닉네임 조회 실패", e)
            }
    }

    // 닉네임 변경 화면으로 이동
    private fun handleChangeNickname() {
        val intent = Intent(this, NicknameSetupActivity::class.java)
        intent.putExtra(NicknameSetupActivity.EXTRA_MODE, NicknameSetupActivity.MODE_UPDATE)
        startActivity(intent)
    }

    // 비밀번호 변경 로직
    private fun handleChangePassword() {
        val currentUser = auth.currentUser ?: return

        // 사용자의 로그인 제공자 확인
        val providerId = currentUser.providerData.find { it.providerId == EmailAuthProvider.PROVIDER_ID }

        if (providerId != null) {
            // 이메일/비밀번호 사용자일 경우
            showPasswordResetDialog()
        } else {
            // Google 사용자일 경우
            val googleProvider = currentUser.providerData.find { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            if (googleProvider != null) {
                Toast.makeText(this, "Google 계정 사용자는 Google에서 비밀번호를 변경해야 합니다.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "이 계정은 비밀번호를 변경할 수 없습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 비밀번호 재설정 이메일 발송 확인 다이얼로그
    private fun showPasswordResetDialog() {
        val userEmail = auth.currentUser?.email
        if (userEmail == null) {
            Toast.makeText(this, "이메일 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("비밀번호 변경")
            .setMessage("$userEmail 주소로 비밀번호 재설정 링크를 보내시겠습니까?")
            .setPositiveButton("보내기") { _, _ ->
                auth.sendPasswordResetEmail(userEmail)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "이메일을 발송했습니다. 메일함을 확인해주세요.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "이메일 발송에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            Log.e("PasswordReset", "발송 실패", task.exception)
                        }
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 회원 탈퇴 확인 다이얼로그
    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("회원 탈퇴")
            .setMessage("정말로 탈퇴하시겠습니까? 작성한 게시글을 포함한 계정과 관련된 모든 데이터가 영구적으로 삭제되며, 복구할 수 없습니다.")
            .setPositiveButton("탈퇴하기") { _, _ ->
                deleteUserAccount()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteUserAccount() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val userUid = currentUser.uid

        // 회원 탈퇴 프로세스 시작
        // 1. 사용자가 작성한 모든 레시피 삭제
        deleteUserRecipes(userUid) { recipeDeletionSuccess ->
            if (recipeDeletionSuccess) {
                // 2. Firestore에서 사용자 프로필 문서 삭제
                deleteUserProfile(userUid) { profileDeletionSuccess ->
                    if (profileDeletionSuccess) {
                        // 3. Firebase Authentication에서 사용자 계정 삭제
                        deleteFirebaseAuthUser(currentUser)
                    } else {
                        // 프로필 삭제 실패 시 처리
                        Toast.makeText(this, "사용자 정보 삭제에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                // 레시피 삭제 실패 시 처리
                Toast.makeText(this, "게시물 삭제에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 1단계: 사용자가 작성한 모든 레시피를 삭제
     * @param uid 삭제할 사용자의 UID
     * @param onComplete 작업 완료 후 결과를 전달하는 콜백 함수
     */
    private fun deleteUserRecipes(uid: String, onComplete: (Boolean) -> Unit) {
        firestore.collection("Recipes").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { querySnapshot ->
                // 한 번에 여러 문서를 삭제하기 위해 WriteBatch 사용 (효율적)
                val batch = firestore.batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d("DeleteAccount", "사용자의 모든 레시피 삭제 성공 (총 ${querySnapshot.size()}개)")
                        onComplete(true) // 성공 콜백 호출
                    }
                    .addOnFailureListener { e ->
                        Log.e("DeleteAccount", "레시피 일괄 삭제 실패", e)
                        onComplete(false) // 실패 콜백 호출
                    }
            }
            .addOnFailureListener { e ->
                Log.e("DeleteAccount", "사용자의 레시피 조회 실패", e)
                onComplete(false) // 실패 콜백 호출
            }
    }

    /**
     * 2단계: Firestore의 Users 컬렉션에서 사용자 프로필 문서를 삭제
     * @param uid 삭제할 사용자의 UID
     * @param onComplete 작업 완료 후 결과를 전달하는 콜백 함수
     */
    private fun deleteUserProfile(uid: String, onComplete: (Boolean) -> Unit) {
        firestore.collection("Users").document(uid).delete()
            .addOnSuccessListener {
                Log.d("DeleteAccount", "Firestore 사용자 프로필 문서 삭제 성공")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("DeleteAccount", "Firestore 사용자 프로필 문서 삭제 실패", e)
                onComplete(false)
            }
    }

    /**
     * 3단계: Firebase Authentication에서 사용자 계정을 삭제
     * @param user 삭제할 FirebaseUser 객체
     */
    private fun deleteFirebaseAuthUser(user: com.google.firebase.auth.FirebaseUser) {
        user.delete()
            .addOnSuccessListener {
                Log.d("DeleteAccount", "Firebase Auth 계정 삭제 성공")
                Toast.makeText(this, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                navigateToLoginActivity()
            }
            .addOnFailureListener { e ->
                Log.e("DeleteAccount", "Firebase Auth 계정 삭제 실패", e)
                Toast.makeText(this, "계정 삭제에 실패했습니다. 다시 로그인 후 시도해주세요.", Toast.LENGTH_LONG).show()
                // 이 경우, 사용자를 로그아웃시키고 다시 로그인하도록 유도
                logoutUser()
            }
    }

    private fun logoutUser() {
        auth.signOut()
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
        navigateToLoginActivity()
    }

    private fun navigateToNicknameSetup() {
        val intent = Intent(this, NicknameSetupActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}