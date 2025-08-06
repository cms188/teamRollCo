package com.example.recipe_pocket.ui.review

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ItemReviewImageBinding

class ReviewImageAdapter(
    private val uris: MutableList<Uri>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<ReviewImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(val binding: ItemReviewImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uri: Uri) {
            Glide.with(binding.root.context).load(uri).into(binding.ivReviewImage)
            binding.btnRemoveImage.setOnClickListener {
                onRemoveClick(adapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemReviewImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(uris[position])
    }

    override fun getItemCount(): Int = uris.size
}