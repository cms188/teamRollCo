package com.example.recipe_pocket.ui.tip

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.data.CookingTip
import com.example.recipe_pocket.databinding.ItemCookTipListBinding
import java.text.SimpleDateFormat
import java.util.*

class CookTipListAdapter(
    private var tips: List<CookingTip>,
    private val onItemClick: (CookingTip) -> Unit
) : RecyclerView.Adapter<CookTipListAdapter.TipViewHolder>() {

    inner class TipViewHolder(private val binding: ItemCookTipListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tip: CookingTip) {
            binding.tvTipTitle.text = tip.title
            binding.tvAuthor.text = tip.author?.nickname ?: "익명"
            binding.tvTimestamp.text = formatTimestamp(tip.createdAt)
            binding.tvCommentCount.text = tip.commentCount.toString()

            // 추천수 - 비추천수
            val score = (tip.likedBy?.size ?: 0) - (tip.dislikedBy?.size ?: 0)
            binding.tvLikeCount.text = score.toString()

            binding.root.setOnClickListener { onItemClick(tip) }
        }

        private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
            if (timestamp == null) return ""
            val sdf = SimpleDateFormat("MM.dd HH:mm", Locale.KOREAN)
            return sdf.format(timestamp.toDate())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val binding = ItemCookTipListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        holder.bind(tips[position])
    }

    override fun getItemCount(): Int = tips.size

    fun updateTips(newTips: List<CookingTip>) {
        this.tips = newTips
        notifyDataSetChanged()
    }
}