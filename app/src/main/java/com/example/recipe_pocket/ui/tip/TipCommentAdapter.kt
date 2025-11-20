package com.example.recipe_pocket.ui.tip

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.TipComment
import com.example.recipe_pocket.databinding.ItemTipCommentBinding
import com.example.recipe_pocket.ui.user.UserFeedActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class TipCommentAdapter(
    private var comments: List<TipComment>,
    private val onDeleteClick: (TipComment) -> Unit,
    private val onReplyClick: (TipComment) -> Unit
) : RecyclerView.Adapter<TipCommentAdapter.CommentViewHolder>() {

    inner class CommentViewHolder(private val binding: ItemTipCommentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(comment: TipComment) {
            binding.tvNickname.text = comment.userNickname ?: "사용자"
            val commentText = if (comment.parentId != null && comment.recipientNickname != null) {
                "@${comment.recipientNickname} ${comment.comment}"
            } else {
                comment.comment
            }
            binding.tvComment.text = commentText
            binding.tvTimestamp.text = comment.createdAt?.toDate()?.let {
                SimpleDateFormat("MM.dd HH:mm", Locale.KOREAN).format(it)
            } ?: ""

            if (!comment.userProfileUrl.isNullOrEmpty()) {
                Glide.with(itemView.context).load(comment.userProfileUrl).into(binding.ivProfile)
            } else {
                binding.ivProfile.setImageResource(R.drawable.ic_profile_placeholder)
            }

            val currentUser = Firebase.auth.currentUser
            if (currentUser != null && currentUser.uid == comment.userId) {
                binding.btnDeleteComment.visibility = View.VISIBLE
                binding.btnDeleteComment.setOnClickListener {
                    onDeleteClick(comment)
                }
            } else {
                binding.btnDeleteComment.visibility = View.GONE
            }

            if (comment.parentId != null) {
                binding.replyIndicator.visibility = View.VISIBLE
                (binding.root.layoutParams as ViewGroup.MarginLayoutParams).marginStart = 60
            } else {
                binding.replyIndicator.visibility = View.GONE
                (binding.root.layoutParams as ViewGroup.MarginLayoutParams).marginStart = 0
            }

            // 로그인한 사용자라면 자신의 댓글을 포함한 모든 댓글에 답글을 달 수 있도록 수정
            if (currentUser != null) {
                binding.btnReply.visibility = View.VISIBLE
                binding.btnReply.setOnClickListener {
                    onReplyClick(comment)
                }
            } else {
                binding.btnReply.visibility = View.GONE
            }

            if (comment.userId != null) {
                val profileClickListener = View.OnClickListener {
                    val intent = Intent(itemView.context, UserFeedActivity::class.java).apply {
                        putExtra(UserFeedActivity.EXTRA_USER_ID, comment.userId)
                    }
                    itemView.context.startActivity(intent)
                }
                binding.ivProfile.setOnClickListener(profileClickListener)
                binding.tvNickname.setOnClickListener(profileClickListener)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemTipCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    fun updateComments(newComments: List<TipComment>) {
        this.comments = newComments
        notifyDataSetChanged()
    }
}