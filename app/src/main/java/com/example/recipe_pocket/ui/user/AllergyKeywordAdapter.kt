package com.example.recipe_pocket.ui.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.databinding.ItemAllergyKeywordBinding

class AllergyKeywordAdapter(
    private val keywords: MutableList<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<AllergyKeywordAdapter.KeywordViewHolder>() {

    inner class KeywordViewHolder(private val binding: ItemAllergyKeywordBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(keyword: String) {
            binding.tvKeyword.text = keyword
            binding.btnDelete.setOnClickListener {
                onDeleteClick(keyword)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeywordViewHolder {
        val binding = ItemAllergyKeywordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return KeywordViewHolder(binding)
    }

    override fun onBindViewHolder(holder: KeywordViewHolder, position: Int) {
        holder.bind(keywords[position])
    }

    override fun getItemCount(): Int = keywords.size

    fun updateKeywords(newKeywords: List<String>) {
        keywords.clear()
        keywords.addAll(newKeywords)
        notifyDataSetChanged()
    }
}