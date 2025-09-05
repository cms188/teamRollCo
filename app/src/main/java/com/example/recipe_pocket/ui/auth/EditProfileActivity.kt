package com.example.recipe_pocket.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.example.recipe_pocket.ChangePasswordActivity
import com.example.recipe_pocket.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var profileImageView: ImageView
    private lateinit var changeProfileImageButton: FloatingActionButton
    private lateinit var currentNicknameTextView: TextView

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfilePicture(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }

        // UI 요소 연결 및 리스너 설정
        val backButton: ImageButton = findViewById(R.id.back_button)
        val changeNicknameButton: LinearLayout = findViewById(R.id.btnChangeNickname)
        val changePasswordButton: LinearLayout = findViewById(R.id.btnChangePassword)
        val logoutButton: LinearLayout = findViewById(R.id.btnLogout)
        val deleteAccountButton: LinearLayout = findViewById(R.id.btnDeleteAccount)
        //프로필 이미지
        profileImageView  = findViewById(R.id.imageView_currentProfile)
        changeProfileImageButton = findViewById(R.id.btnChangeProfileImage)
        currentNicknameTextView = findViewById(R.id.tvCurrentNickname)

        loadUserProfile()

        backButton.setOnClickListener { finish() } // 뒤로가기 버튼
        changeNicknameButton.setOnClickListener { handleChangeNickname() }
        changePasswordButton.setOnClickListener { handleChangePassword() }
        logoutButton.setOnClickListener { logoutUser() }
        deleteAccountButton.setOnClickListener { showDeleteAccountConfirmation() }
        changeProfileImageButton.setOnClickListener { openGallery() }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("Users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    var nickname = document.getString("nickname")
                    if (!nickname.isNullOrEmpty()) {
                        currentNicknameTextView.text = nickname
                    } else {
                        currentNicknameTextView.text = "닉네임 없음"
                    }
                    val imageUrl = document.getString("profileImageUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(imageUrl)
                            .placeholder(R.drawable.bg_author_profile)
                            .error(R.drawable.bg_author_profile)
                            .circleCrop()
                            .into(profileImageView)
                    } else {
                        profileImageView.setImageResource(R.drawable.bg_author_profile)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditProfile", "프로필 이미지 로드 실패", e)
                currentNicknameTextView.text = "닉네임 없음"
                profileImageView.setImageResource(R.drawable.bg_author_profile)
            }
    }
    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    private fun uploadProfilePicture(imageUri: Uri) {
        val currentUser = auth.currentUser ?: return
        val storageRef = storage.reference.child("profile_pictures/${currentUser.uid}")

        Toast.makeText(this, "프로필 사진을 업로드 중입니다...", Toast.LENGTH_SHORT).show()

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    updateProfileUrlInFirestore(uri.toString())
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "업로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfileUrlInFirestore(url: String) {
        val currentUser = auth.currentUser ?: return

        firestore.collection("Users").document(currentUser.uid)
            .update("profileImageUrl", url)
            .addOnSuccessListener {
                Toast.makeText(this, "프로필 사진이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                // 화면에 즉시 반영
                Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.bg_author_profile)
                    .error(R.drawable.bg_author_profile)
                    .circleCrop()
                    .into(profileImageView)
            }
            .addOnFailureListener {
                Toast.makeText(this, "프로필 사진 정보 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    // 닉네임 변경 화면으로 이동
    private fun handleChangeNickname() {
        val intent = Intent(this, NicknameSetupActivity::class.java)
        // 닉네임 변경 모드로 실행
        intent.putExtra(NicknameSetupActivity.EXTRA_MODE, NicknameSetupActivity.MODE_UPDATE)
        startActivity(intent)
    }

    // 비밀번호 변경 로직
    private fun handleChangePassword() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("Users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val loginType = document.getString("loginType")

                    // loginType이 google 또는 kakao인 경우 차단
                    if (loginType == "google" || loginType == "kakao") {
                        when (loginType) {
                            "google" -> {
                                Toast.makeText(this, "Google 계정으로 소셜 로그인한 사용자는 \n 비밀번호를 변경할 수 없습니다.", Toast.LENGTH_LONG).show()
                            }
                            "kakao" -> {
                                Toast.makeText(this, "카카오 계정으로 소셜 로그인한 사용자는 \n 비밀번호를 변경할 수 없습니다.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        ChangePassword_Ord()
                    }
                } else {
                    ChangePassword_Ord()
                }
            }
            .addOnFailureListener { e ->
                Log.e("EditProfile", "loginType 확인 실패", e)
                Toast.makeText(
                    this,
                    "알 수 없는 오류가 발생했습니다.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    //비밀번호 변경 로직 (기존꺼)
    private fun ChangePassword_Ord() {
        val currentUser = auth.currentUser ?: return

        // 사용자의 로그인 제공자 확인
        val providerId = currentUser.providerData.find { it.providerId == EmailAuthProvider.PROVIDER_ID }

        if (providerId != null) {
            // 이메일/비밀번호 사용자일 경우
            //showPasswordResetDialog()
            val intent = Intent(this, ChangePasswordActivity::class.java)
            startActivity(intent)

        } else {
            // 소셜 로그인 사용자일 경우
            val googleProvider = currentUser.providerData.find { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            if (googleProvider != null) {
                Toast.makeText(this, "Google 계정 사용자는 Google에서 비밀번호를 변경해야 합니다.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "이 계정은 비밀번호를 변경할 수 없습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 비밀번호 재설정 이메일 발송 확인 다이얼로그
    /*private fun showPasswordResetDialog() {
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
    }*/

    // 로그아웃
    private fun logoutUser() {
        auth.signOut()
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show()
        // 모든 액티비티 스택을 지우고 로그인 화면으로 이동
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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

    // 회원 탈퇴 전체 프로세스
    private fun deleteUserAccount() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val userUid = currentUser.uid

        // 1. 사용자가 작성한 모든 레시피 삭제
        deleteUserRecipes(userUid) { recipeDeletionSuccess ->
            if (recipeDeletionSuccess) {
                // 2. Firestore에서 사용자 프로필 문서 삭제
                deleteUserProfile(userUid) { profileDeletionSuccess ->
                    if (profileDeletionSuccess) {
                        // 3. Firebase Authentication에서 사용자 계정 삭제
                        deleteFirebaseAuthUser(currentUser)
                    } else {
                        Toast.makeText(this, "사용자 정보 삭제에 실패했습니다.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "게시물 삭제에 실패했습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun deleteUserRecipes(uid: String, onComplete: (Boolean) -> Unit) {
        firestore.collection("Recipes").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { querySnapshot ->
                val batch = firestore.batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d("DeleteAccount", "사용자의 모든 레시피 삭제 성공")
                        onComplete(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("DeleteAccount", "레시피 일괄 삭제 실패", e)
                        onComplete(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("DeleteAccount", "사용자의 레시피 조회 실패", e)
                onComplete(false)
            }
    }

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

    private fun deleteFirebaseAuthUser(user: FirebaseUser) {
        user.delete()
            .addOnSuccessListener {
                Log.d("DeleteAccount", "Firebase Auth 계정 삭제 성공")
                Toast.makeText(this, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                // 모든 스택을 지우고 로그인 화면으로 이동
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("DeleteAccount", "Firebase Auth 계정 삭제 실패", e)
                Toast.makeText(this, "계정 삭제에 실패했습니다. 다시 로그인 후 시도해주세요.", Toast.LENGTH_LONG).show()
                // 이 경우, 사용자를 로그아웃시키고 다시 로그인하도록 유도
                logoutUser()
            }
    }
}