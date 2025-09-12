package com.example.recipe_pocket.ui.main

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.data.SeasonalIngredient
import com.example.recipe_pocket.databinding.ItemSeasonalIngredientBinding
import com.example.recipe_pocket.ui.recipe.search.SearchResult

class SeasonalIngredientAdapter(
    private var ingredients: List<SeasonalIngredient>
) : RecyclerView.Adapter<SeasonalIngredientAdapter.IngredientViewHolder>() {

    inner class IngredientViewHolder(private val binding: ItemSeasonalIngredientBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(ingredient: SeasonalIngredient) {
            binding.tvIngredientName.text = ingredient.name
            Glide.with(itemView.context)
                .load(ingredient.imageUrl)
                .into(binding.ivIngredientImage)

            itemView.setOnClickListener {
                val intent = Intent(itemView.context, SearchResult::class.java).apply {
                    // SearchResult 액티비티로 검색어를 전달
                    putExtra("search_query", ingredient.name)
                }
                itemView.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemSeasonalIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(ingredients[position])
    }

    override fun getItemCount(): Int = ingredients.size

    fun updateIngredients(newIngredients: List<SeasonalIngredient>) {
        this.ingredients = newIngredients
        notifyDataSetChanged()
    }
}