package com.example.recipe_pocket.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.CookingTip
import com.example.recipe_pocket.databinding.CookTip01Binding

class CookTipAdapter(
    private var items: List<CookingTip>,
    private val onItemClick: (CookingTip) -> Unit
) : RecyclerView.Adapter<CookTipAdapter.CookTipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CookTipViewHolder {
        val binding = CookTip01Binding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CookTipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CookTipViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<CookingTip>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class CookTipViewHolder(private val binding: CookTip01Binding) : RecyclerView.ViewHolder(binding.root) {
        private val mainTextView: TextView = binding.mainContentText
        private val subTextView: TextView = binding.subContentText
        private val imageView: ImageView = binding.recipeImageView

        fun bind(cookTip: CookingTip) {
            mainTextView.text = cookTip.title

            val firstImageUrl = cookTip.content?.firstOrNull { !it.imageUrl.isNullOrEmpty() }?.imageUrl
            if (firstImageUrl != null) {
                Glide.with(itemView.context)
                    .load(firstImageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .error(R.drawable.bg_no_img_gray)
                    .into(imageView)
            } else {
                imageView.setImageResource(R.drawable.bg_no_img_gray)
            }

            itemView.setOnClickListener {
                onItemClick(cookTip)
            }
        }
    }
}