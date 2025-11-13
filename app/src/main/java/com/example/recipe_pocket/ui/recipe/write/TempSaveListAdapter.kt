package com.example.recipe_pocket.ui.recipe.write

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.data.TempSaveDraft
import com.example.recipe_pocket.databinding.ItemCookTipListBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TempSaveListAdapter(
    private var drafts: List<TempSaveDraft>,
    private val onItemClick: (TempSaveDraft) -> Unit
) : RecyclerView.Adapter<TempSaveListAdapter.TempSaveViewHolder>() {

    inner class TempSaveViewHolder(private val binding: ItemCookTipListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val formatter = SimpleDateFormat("MM.dd HH:mm", Locale.KOREAN)

        fun bind(draft: TempSaveDraft) {
            binding.tvTipTitle.text = draft.recipe.title.ifBlank { "제목 없음" }
            binding.tvAuthor.text = draft.recipe.description.ifBlank { "제목 없음" }
            binding.tvTimestamp.text = draft.updatedAt?.let { formatter.format(it.toDate()) } ?: ""

            binding.tvLikeCount.visibility = View.GONE
            binding.tvCommentCount.visibility = View.GONE
            binding.ivLikeIcon?.visibility = View.GONE
            binding.ivCommentIcon?.visibility = View.GONE

            binding.root.setOnClickListener { onItemClick(draft) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TempSaveViewHolder {
        val binding = ItemCookTipListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TempSaveViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TempSaveViewHolder, position: Int) {
        holder.bind(drafts[position])
    }

    override fun getItemCount(): Int = drafts.size

    fun updateDrafts(newDrafts: List<TempSaveDraft>) {
        drafts = newDrafts
        notifyDataSetChanged()
    }
}
