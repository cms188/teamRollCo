package com.example.recipe_pocket.ui.review

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.ItemReviewPhotoBinding

class ReviewImageDisplayAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<ReviewImageDisplayAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(private val binding: ItemReviewPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(url: String) {
            Glide.with(itemView.context)
                .load(url)
                .into(binding.ivReviewPhoto)

            itemView.setOnClickListener {
                val intent = Intent(itemView.context, PhotoViewActivity::class.java).apply {
                    putExtra(PhotoViewActivity.EXTRA_IMAGE_URL, url)
                }
                itemView.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemReviewPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(imageUrls[position])
    }

    override fun getItemCount(): Int = imageUrls.size
}