package com.example.recipe_pocket.ui.user

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.databinding.ActivityFollowListBinding
import com.example.recipe_pocket.repository.NotificationHandler // import 확인
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FollowListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFollowListBinding
    private lateinit var userAdapter: UserAdapter

    private var targetUserId: String? = null
    private var mode: String? = null // "followers" 또는 "following"

    private val db = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFollowListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent로부터 사용자 ID와 모드를 가져옴
        targetUserId = intent.getStringExtra("USER_ID")
        mode = intent.getStringExtra("MODE")

        // 필수 데이터가 없으면 Activity 종료
        if (targetUserId == null || mode == null) {
            finish()
            return
        }

        setupUI()
        setupRecyclerView()
        loadFollowList()
    }

    /**
     * 상단 바 타이틀 설정 및 뒤로가기 버튼 리스너 설정
     */
    private fun setupUI() {
        if (mode == "followers") {
            utils.ToolbarUtils.setupTransparentToolbar(this, "팔로워")
        } else {
            utils.ToolbarUtils.setupTransparentToolbar(this, "팔로잉")
        }
    }

    /**
     * RecyclerView 및 UserAdapter 초기화
     */
    private fun setupRecyclerView() {
        // 어댑터 초기화 시, 팔로우 버튼 클릭 이벤트를 처리할 람다 함수를 전달
        userAdapter = UserAdapter(emptyList()) { user, position ->
            toggleFollow(user, position)
        }
        binding.recyclerViewFollow.apply {
            layoutManager = LinearLayoutManager(this@FollowListActivity)
            adapter = userAdapter
        }
    }

    /**
     * 팔로워 또는 팔로잉 목록의 ID들을 Firestore에서 가져옴
     */
    private fun loadFollowList() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyList.visibility = View.GONE

        // 모드에 따라 쿼리할 서브 컬лек션 경로 결정
        val collectionPath = if (mode == "followers") "followers" else "following"

        db.collection("Users").document(targetUserId!!).collection(collectionPath).get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmptyList.text = when (mode) {
                        "followers" -> "아직 팔로워가 없어요.\n다른 사용자들과 소통해보세요!"
                        "following" -> "아직 팔로우하는 사용자가 없어요.\n관심있는 사용자를 팔로우해보세요!"
                        else -> "목록이 비어있어요."
                    }
                    binding.tvEmptyList.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // 성공 시, ID 목록으로 사용자 상세 정보 요청
                val userIds = documents.map { it.id }
                fetchUsersData(userIds)

            }.addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyList.text = when (mode) {
                    "followers" -> "팔로워 목록을 불러올 수 없어요."
                    "following" -> "팔로잉 목록을 불러올 수 없어요."
                    else -> "목록을 불러오는데 실패했어요."
                }
                binding.tvEmptyList.visibility = View.VISIBLE
            }
    }


    /**
     * ID 목록을 기반으로 각 사용자의 상세 정보(닉네임, 프로필 사진 등)를 가져옴
     */
    private fun fetchUsersData(userIds: List<String>) {
        // 비동기 처리를 위해 코루틴 사용
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 현재 로그인한 유저가 팔로우하는 사람들 목록을 미리 가져와서 Set으로 만듦 (빠른 조회를 위해)
                val myFollowingSet = currentUser?.uid?.let { uid ->
                    db.collection("Users").document(uid).collection("following").get()
                        .await().documents.map { it.id }.toSet()
                } ?: emptySet()

                // 각 ID에 해당하는 사용자 정보를 가져와 UserAdapter에서 사용할 데이터 클래스로 변환
                val userList = userIds.map { userId ->
                    val userDoc = db.collection("Users").document(userId).get().await()
                    if (userDoc.exists()) {
                        UserAdapter.UserWithId(
                            id = userDoc.id,
                            nickname = userDoc.getString("nickname"),
                            profileImageUrl = userDoc.getString("profileImageUrl"),
                            isFollowing = myFollowingSet.contains(userDoc.id) // 내가 팔로우하는지 여부 체크
                        )
                    } else {
                        null // 문서가 없는 경우 null 반환
                    }
                }.filterNotNull() // null이 아닌 항목만 필터링

                // UI 업데이트는 메인 스레드에서 수행
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    userAdapter.updateUserList(userList)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmptyList.text = "사용자 정보를 가져오는데 실패했어요."
                    binding.tvEmptyList.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * 팔로우/언팔로우 상태를 토글하는 함수
     */
    private fun toggleFollow(user: UserAdapter.UserWithId, position: Int) {
        if (currentUser == null) {
            // 로그인이 되어있지 않으면 아무것도 하지 않음
            return
        }
        if (currentUser.uid == user.id) return // 자기 자신은 팔로우/언팔로우 불가

        // Firestore 문서 참조 경로 설정
        val myFollowingRef =
            db.collection("Users").document(currentUser.uid).collection("following")
                .document(user.id)
        val targetFollowerRef = db.collection("Users").document(user.id).collection("followers")
            .document(currentUser.uid)
        val myUserDocRef = db.collection("Users").document(currentUser.uid)
        val targetUserDocRef = db.collection("Users").document(user.id)

        // 여러 문서를 안전하게 업데이트하기 위해 트랜잭션 사용
        db.runTransaction { transaction ->
            if (user.isFollowing) { // 이미 팔로우 중일 경우 -> 언팔로우
                transaction.delete(myFollowingRef)
                transaction.delete(targetFollowerRef)
                transaction.update(myUserDocRef, "followingCount", FieldValue.increment(-1))
                transaction.update(targetUserDocRef, "followerCount", FieldValue.increment(-1))
            } else { // 팔로우하지 않을 경우 -> 팔로우
                transaction.set(myFollowingRef, mapOf("followedAt" to FieldValue.serverTimestamp()))
                transaction.set(
                    targetFollowerRef,
                    mapOf("followedAt" to FieldValue.serverTimestamp())
                )
                transaction.update(myUserDocRef, "followingCount", FieldValue.increment(1))
                transaction.update(targetUserDocRef, "followerCount", FieldValue.increment(1))
            }
        }.addOnSuccessListener {
            lifecycleScope.launch {
                if (!user.isFollowing) {
                    // 새로 팔로우 했을 때 'add' 함수 호출
                    NotificationHandler.addFollowNotification(
                        recipientId = user.id,
                        senderId = currentUser.uid
                    )
                } else {
                    // 언팔로우 했을 때 'remove' 함수 호출
                    NotificationHandler.removeFollowNotification(
                        recipientId = user.id,
                        senderId = currentUser.uid
                    )
                }
            }

            // 트랜잭션 성공 시 UI 업데이트
            user.isFollowing = !user.isFollowing
            userAdapter.notifyItemChanged(position)
        }.addOnFailureListener { e ->
            Log.w("FollowToggle", "Transaction failed.", e)
        }
    }
}