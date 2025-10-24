package com.example.recipe_pocket.ui.tip

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.CookingTip
import com.example.recipe_pocket.data.TipComment
import com.example.recipe_pocket.databinding.ActivityCookTipDetailBinding
import com.example.recipe_pocket.repository.NotificationHandler
import com.example.recipe_pocket.ui.user.UserFeedActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

class CookTipDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookTipDetailBinding
    private lateinit var commentAdapter: TipCommentAdapter
    private var tipId: String? = null
    private var currentTip: CookingTip? = null
    private var replyingToComment: TipComment? = null // 답글 작성 대상 댓글 저장

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    companion object {
        const val EXTRA_TIP_ID = "TIP_ID"
    }

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadTipData()
            loadComments()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookTipDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tipId = intent.getStringExtra(EXTRA_TIP_ID)
        if (tipId == null) {
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadTipData()
        loadComments()
        utils.ToolbarUtils.setupTransparentToolbar(this, "요리 Tip", showEditButton = true, showDeleteButton = true)
        setupWindowInsets()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupToolbar() {
        //binding.ivBackButton.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        commentAdapter = TipCommentAdapter(
            emptyList(),
            onDeleteClick = { comment ->
                showDeleteCommentDialog(comment)
            },
            onReplyClick = { comment ->
                replyingToComment = comment
                updateCommentInputUI()
            }
        )
        binding.recyclerViewComments.apply {
            adapter = commentAdapter
            layoutManager = LinearLayoutManager(this@CookTipDetailActivity)
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        // 추천/비추천 버튼 클릭 리스너
        binding.btnLike.setOnClickListener {
            if (auth.currentUser?.uid == currentTip?.userId) {
                Toast.makeText(this, "자신의 글에는 추천할 수 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                toggleLikeDislike(true)
            }
        }
        binding.btnDislike.setOnClickListener {
            if (auth.currentUser?.uid == currentTip?.userId) {
                Toast.makeText(this, "자신의 글에는 비추천할 수 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                toggleLikeDislike(false)
            }
        }

        binding.btnSubmitComment.setOnClickListener { submitComment() }
        binding.btnCancelReply.setOnClickListener {
            replyingToComment = null
            updateCommentInputUI()
        }
    }

    private fun loadTipData() {
        lifecycleScope.launch {
            try {
                val document = db.collection("CookingTips").document(tipId!!).get().await()
                currentTip = document.toObject(CookingTip::class.java)?.apply { id = document.id }

                if (currentTip == null) {
                    Toast.makeText(this@CookTipDetailActivity, "요리 팁을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                populateUI()
            } catch (e: Exception) {
                Toast.makeText(this@CookTipDetailActivity, "데이터 로딩 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun populateUI() {
        val tip = currentTip ?: return
        val currentUser = auth.currentUser

        binding.tvTipTitle.text = tip.title
        binding.tvTimestamp.text = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREAN).format(tip.createdAt!!.toDate())

        loadAuthorInfo(tip.userId)
        renderContentBlocks(tip)
        updateLikeDislikeUI()

        // 본인 게시글일 때 수정/삭제 버튼 표시
        if (currentUser != null && currentUser.uid == tip.userId) {
            binding.editDeleteContainer.visibility = View.VISIBLE
            binding.btnDeleteTip.setOnClickListener { showDeleteTipDialog() }
            binding.btnEditTip.setOnClickListener {
                val intent = Intent(this, CookTipWriteActivity::class.java).apply {
                    putExtra(CookTipWriteActivity.EXTRA_MODE, CookTipWriteActivity.MODE_EDIT)
                    putExtra(CookTipWriteActivity.EXTRA_TIP_ID, tip.id)
                }
                editLauncher.launch(intent)
            }
        } else {
            binding.editDeleteContainer.visibility = View.GONE
        }
    }

    private fun loadAuthorInfo(userId: String?) {
        if (userId == null) return
        db.collection("Users").document(userId).get().addOnSuccessListener { doc ->
            if(doc.exists()) {
                binding.tvAuthorName.text = doc.getString("nickname")
                val imageUrl = doc.getString("profileImageUrl")
                if (!imageUrl.isNullOrEmpty()) {
                    Glide.with(this).load(imageUrl).into(binding.ivProfile)
                }
                binding.authorInfoLayout.setOnClickListener {
                    val intent = Intent(this, UserFeedActivity::class.java)
                    intent.putExtra(UserFeedActivity.EXTRA_USER_ID, userId)
                    startActivity(intent)
                }
            }
        }
    }

    private fun renderContentBlocks(tip: CookingTip) {
        binding.contentContainer.removeAllViews()
        tip.content?.forEach { block ->
            if (!block.text.isNullOrBlank()) {
                val textView = LayoutInflater.from(this).inflate(R.layout.item_cook_tip_detail_text, binding.contentContainer, false) as TextView
                textView.text = block.text
                binding.contentContainer.addView(textView)
            }
            if (!block.imageUrl.isNullOrBlank()) {
                val imageView = LayoutInflater.from(this).inflate(R.layout.item_cook_tip_detail_image, binding.contentContainer, false) as ImageView
                Glide.with(this).load(block.imageUrl).into(imageView)
                binding.contentContainer.addView(imageView)
            }
        }
    }

    private fun toggleLikeDislike(isLike: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        val tipRef = db.collection("CookingTips").document(tipId!!)
        val tip = currentTip ?: return

        db.runTransaction { transaction ->
            val snapshot = transaction.get(tipRef)
            val likedBy = snapshot.get("likedBy") as? MutableList<String> ?: mutableListOf()
            val dislikedBy = snapshot.get("dislikedBy") as? MutableList<String> ?: mutableListOf()

            val isAlreadyLiked = likedBy.contains(userId)
            val isAlreadyDisliked = dislikedBy.contains(userId)
            var scoreChange = 0

            if (isLike) {
                if (isAlreadyLiked) {
                    likedBy.remove(userId)
                    scoreChange = -1
                } else {
                    likedBy.add(userId)
                    scoreChange = 1
                    if (isAlreadyDisliked) {
                        dislikedBy.remove(userId)
                        scoreChange = 2
                    }
                }
            } else {
                if (isAlreadyDisliked) {
                    dislikedBy.remove(userId)
                    scoreChange = 1
                } else {
                    dislikedBy.add(userId)
                    scoreChange = -1
                    if (isAlreadyLiked) {
                        likedBy.remove(userId)
                        scoreChange = -2
                    }
                }
            }

            transaction.update(tipRef, "likedBy", likedBy)
            transaction.update(tipRef, "dislikedBy", dislikedBy)
            transaction.update(tipRef, "recommendationScore", FieldValue.increment(scoreChange.toLong()))

        }.addOnSuccessListener {
            val likedBy = tip.likedBy?.toMutableList() ?: mutableListOf()
            val dislikedBy = tip.dislikedBy?.toMutableList() ?: mutableListOf()
            val isAlreadyLiked = likedBy.contains(userId)
            val isAlreadyDisliked = dislikedBy.contains(userId)

            if (isLike) {
                if (isAlreadyLiked) likedBy.remove(userId)
                else {
                    likedBy.add(userId)
                    if (isAlreadyDisliked) dislikedBy.remove(userId)
                }
            } else {
                if (isAlreadyDisliked) dislikedBy.remove(userId)
                else {
                    dislikedBy.add(userId)
                    if (isAlreadyLiked) likedBy.remove(userId)
                }
            }
            currentTip = tip.copy(likedBy = likedBy, dislikedBy = dislikedBy)
            updateLikeDislikeUI()
        }
    }

    private fun updateLikeDislikeUI() {
        val tip = currentTip ?: return
        val userId = auth.currentUser?.uid

        val likeCount = tip.likedBy?.size ?: 0
        val dislikeCount = tip.dislikedBy?.size ?: 0

        binding.tvLikeCount.text = likeCount.toString()
        binding.tvDislikeCount.text = dislikeCount.toString()

        if (userId != null && tip.likedBy?.contains(userId) == true) {
            binding.btnLikeIcon.setColorFilter(ContextCompat.getColor(this, R.color.orange))
        } else {
            binding.btnLikeIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_gray))
        }

        if (userId != null && tip.dislikedBy?.contains(userId) == true) {
            binding.btnDislikeIcon.setColorFilter(ContextCompat.getColor(this, R.color.orange))
        } else {
            binding.btnDislikeIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_gray))
        }
    }

    private fun loadComments() {
        db.collection("CookingTips").document(tipId!!).collection("Comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val allComments = snapshots.toObjects(TipComment::class.java)

                // 댓글과 답글을 올바르게 정렬하는 로직
                val sortedList = mutableListOf<TipComment>()
                // parentId가 없는 댓글(원본 댓글)을 먼저 찾고, 시간순으로 정렬
                val parentComments = allComments.filter { it.parentId == null }.sortedBy { it.createdAt }
                // 답글들을 parentId를 기준으로 그룹화
                val repliesMap = allComments.filter { it.parentId != null }.groupBy { it.parentId!! }

                // 원본 댓글을 순회하며 정렬된 리스트에 추가
                parentComments.forEach { parent ->
                    sortedList.add(parent)
                    // 해당 원본 댓글에 달린 답글이 있다면, 시간순으로 정렬하여 리스트에 추가
                    repliesMap[parent.id]?.sortedBy { it.createdAt }?.let { replies ->
                        sortedList.addAll(replies)
                    }
                }

                commentAdapter.updateComments(sortedList)
                binding.tvCommentHeader.text = "댓글 ${allComments.size}"
            }
    }

    private fun updateCommentInputUI() {
        if (replyingToComment != null) {
            binding.cancelReplyLayout.visibility = View.VISIBLE
            binding.tvReplyTo.text = "@${replyingToComment!!.userNickname}에게 답글 남기는 중..."
            binding.etCommentInput.hint = "답글을 입력하세요..."
            binding.etCommentInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etCommentInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            binding.cancelReplyLayout.visibility = View.GONE
            binding.etCommentInput.hint = "댓글을 입력하세요..."
            binding.etCommentInput.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.etCommentInput.windowToken, 0)
        }
    }

    private fun submitComment() {
        val commentText = binding.etCommentInput.text.toString().trim()
        if (commentText.isEmpty()) return

        val user = auth.currentUser ?: return
        binding.btnSubmitComment.isEnabled = false

        lifecycleScope.launch {
            try {
                val userDoc = db.collection("Users").document(user.uid).get().await()

                val parentId = replyingToComment?.parentId ?: replyingToComment?.id

                val comment = TipComment(
                    tipId = tipId,
                    userId = user.uid,
                    userNickname = userDoc.getString("nickname"),
                    userProfileUrl = userDoc.getString("profileImageUrl"),
                    comment = commentText,
                    createdAt = Timestamp.now(),
                    parentId = parentId,
                    recipientId = replyingToComment?.userId,
                    recipientNickname = replyingToComment?.userNickname
                )

                db.collection("CookingTips").document(tipId!!).collection("Comments").add(comment).await()

                if (replyingToComment == null) {
                    NotificationHandler.addTipCommentNotification(currentTip!!, user.uid)
                } else {
                    NotificationHandler.addTipReplyNotification(currentTip!!, comment)
                }

                binding.etCommentInput.text.clear()
                replyingToComment = null
                updateCommentInputUI()

            } catch (e: Exception) {
                Toast.makeText(this@CookTipDetailActivity, "등록 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSubmitComment.isEnabled = true
            }
        }
    }

    private fun showDeleteTipDialog() {
        AlertDialog.Builder(this)
            .setTitle("게시글 삭제")
            .setMessage("이 요리 팁을 정말 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> deleteTip() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteTip() {
        db.collection("CookingTips").document(tipId!!).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "게시글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun showDeleteCommentDialog(comment: TipComment) {
        AlertDialog.Builder(this)
            .setTitle("댓글 삭제")
            .setMessage("이 댓글을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> deleteComment(comment) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun deleteComment(comment: TipComment) {
        if (comment.id == null) return
        db.collection("CookingTips").document(tipId!!).collection("Comments").document(comment.id!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "댓글이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
    }
}