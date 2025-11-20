package com.example.recipe_pocket.ui.user

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.databinding.ItemTitleBinding

class TitleAdapter(
    private var titleList: List<String>,
    private var currentTitle: String,
    private val onTitleSelect: (String) -> Unit
) : RecyclerView.Adapter<TitleAdapter.TitleViewHolder>() {

    inner class TitleViewHolder(private val binding: ItemTitleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvTitleName.text = title

            // 현재 설정된 칭호인지 확인
            if (title == currentTitle) {
                binding.ivCurrentTitleCheck.visibility = View.VISIBLE
                binding.btnSelectTitle.isEnabled = false
                binding.btnSelectTitle.text = "설정됨"
            } else {
                binding.ivCurrentTitleCheck.visibility = View.INVISIBLE
                binding.btnSelectTitle.isEnabled = true
                binding.btnSelectTitle.text = "설정하기"
            }

            binding.btnSelectTitle.setOnClickListener {
                onTitleSelect(title)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TitleViewHolder {
        val binding = ItemTitleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TitleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TitleViewHolder, position: Int) {
        holder.bind(titleList[position])
    }

    override fun getItemCount(): Int = titleList.size

    fun updateTitles(newTitles: List<String>, newCurrentTitle: String) {
        this.titleList = newTitles
        this.currentTitle = newCurrentTitle
        notifyDataSetChanged()
    }

    fun setCurrentTitle(newCurrentTitle: String) {
        this.currentTitle = newCurrentTitle
        notifyDataSetChanged() // 전체 목록을 갱신하여 UI 변경
    }
}