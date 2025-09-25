package com.example.recipe_pocket.ui.category

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.databinding.ItemCategoryEntryBinding

data class CategoryItem(
    val displayName: String,
    val iconRes: Int,
    val categoryKey: String
)

class CategoryGridAdapter(
    private val items: List<CategoryItem>,
    private val onCategoryClick: (CategoryItem) -> Unit
) : RecyclerView.Adapter<CategoryGridAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCategoryEntryBinding.inflate(inflater, parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CategoryViewHolder(
        private val binding: ItemCategoryEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategoryItem) {
            binding.categoryIcon.setBackgroundResource(item.iconRes)
            binding.categoryName.text = item.displayName
            binding.categoryIcon.contentDescription = item.displayName
            binding.root.setOnClickListener { onCategoryClick(item) }
        }
    }
}
