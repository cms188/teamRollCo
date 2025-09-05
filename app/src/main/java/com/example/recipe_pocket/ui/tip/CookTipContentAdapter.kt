package com.example.recipe_pocket.ui.tip

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.databinding.ItemCookTipContentBlockBinding

class CookTipContentAdapter(
    private var contentBlocks: MutableList<ContentBlockData>,
    private val onImageClick: (Int) -> Unit,
    private val onRemoveClick: (Int) -> Unit,
    private val onTextChange: (Int, String) -> Unit
) : RecyclerView.Adapter<CookTipContentAdapter.ContentViewHolder>() {

    inner class ContentViewHolder(val binding: ItemCookTipContentBlockBinding) : RecyclerView.ViewHolder(binding.root) {
        private var textWatcher: TextWatcher? = null

        fun bind(block: ContentBlockData, position: Int) {
            // 기존 TextWatcher 제거
            binding.etContent.removeTextChangedListener(textWatcher)

            binding.etContent.setText(block.text)

            if (block.imageUri != null) {
                binding.ivContentImage.setImageURI(block.imageUri)
                binding.ivAddIcon.visibility = View.GONE
            } else {
                binding.ivContentImage.setImageResource(0) // 이미지 초기화
                binding.ivAddIcon.visibility = View.VISIBLE
            }

            // 첫 번째 아이템이 아니면 삭제 버튼 표시
            binding.btnRemoveBlock.visibility = if (position > 0) View.VISIBLE else View.GONE

            binding.frameAddImage.setOnClickListener {
                onImageClick(adapterPosition)
            }

            binding.btnRemoveBlock.setOnClickListener {
                onRemoveClick(adapterPosition)
            }

            // 새로운 TextWatcher 설정
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onTextChange(adapterPosition, s.toString())
                }
            }
            binding.etContent.addTextChangedListener(textWatcher)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder {
        val binding = ItemCookTipContentBlockBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) {
        holder.bind(contentBlocks[position], position)
    }

    override fun getItemCount(): Int = contentBlocks.size

    fun updateData(newData: List<ContentBlockData>) {
        contentBlocks.clear()
        contentBlocks.addAll(newData)
        notifyDataSetChanged()
    }
}