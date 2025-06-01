package com.example.recipe_pocket

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.databinding.CookCard01Binding // CookCard01.xml에 대한 바인딩
import com.example.recipe_pocket.databinding.CookCard02Binding // CookCard02.xml에 대한 바인딩
import com.example.recipe_pocket.databinding.CookCard03Binding // CookCard03.xml에 대한 바인딩

class RecipeAdapter(
    private var recipes: List<Recipe>,
    @LayoutRes private val cardLayoutId: Int // 사용할 카드의 레이아웃 ID를 받음
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() { // 모든 ViewHolder의 공통 부모

    // 기본 이미지 ID들은 이전과 동일하게 유지
    private val defaultRecipeImageErrorResId: Int = R.drawable.testimg1
    private val defaultProfileImagePlaceholderResId: Int = R.drawable.bg_main_circle_gray
    private val defaultProfileImageErrorResId: Int = R.drawable.testimg1

    // 각 카드 타입에 대한 ViewHolder 정의 (이전 답변과 유사)
    // 각 ViewHolder는 해당 카드 레이아웃에 맞는 바인딩 객체를 사용해야 합니다.
    inner class Card01ViewHolder(private val binding: CookCard01Binding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipe: Recipe) {
            // 기존 Card03 ViewHolder의 bind 로직과 동일하게 구현
            val context = binding.root.context
            binding.recipeNameText.text = recipe.title ?: "제목 없음"
            binding.cookingTimeText.text = recipe.cookingTime?.let { "${it}분" } ?: "시간 정보 없음"
            // recipe.rating?.let { binding.ratingText.text = String.format("%.1f", it) } ?: run { binding.ratingText.text = "N/A" }
            //binding.difficultyText.text = recipe.difficulty ?: "정보 없음"

            recipe.thumbnailUrl?.let { url ->
                if (url.isNotEmpty()) {
                    Glide.with(context)
                        .load(url)
                        .error(defaultRecipeImageErrorResId)
                        .into(binding.recipeImageView)
                } else {
                    binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
                }
            } ?: run {
                binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
            }

            val author = recipe.author
            if (author != null) {
                binding.authorNameText.text = author.nickname ?: "작성자 정보 없음"
                author.profileImageUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        Glide.with(context)
                            .load(url)
                            .placeholder(defaultProfileImagePlaceholderResId)
                            .error(defaultProfileImageErrorResId)
                            .circleCrop()
                            .into(binding.authorProfileImage)
                    } else {
                        binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
                    }
                } ?: run {
                    binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
                }
            } else {
                binding.authorNameText.text = "작성자 미상"
                binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            }

            binding.root.setOnClickListener {
                recipe.id?.let { recipeId ->
                    if (recipeId.isNotEmpty()) {
                        val intent = Intent(context, RecipeDetailActivity::class.java)
                        intent.putExtra("RECIPE_ID", recipeId)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "레시피 정보를 불러올 수 없습니다. (ID 없음)", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Toast.makeText(context, "레시피 정보를 불러올 수 없습니다. (ID null)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class Card02ViewHolder(private val binding: CookCard02Binding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipe: Recipe) {
            // 기존 Card03 ViewHolder의 bind 로직과 동일하게 구현
            val context = binding.root.context
            binding.recipeNameText.text = recipe.title ?: "제목 없음"
            binding.cookingTimeText.text = recipe.cookingTime?.let { "${it}분" } ?: "시간 정보 없음"
            // recipe.rating?.let { binding.ratingText.text = String.format("%.1f", it) } ?: run { binding.ratingText.text = "N/A" }
            //binding.difficultyText.text = recipe.difficulty ?: "정보 없음"

            recipe.thumbnailUrl?.let { url ->
                if (url.isNotEmpty()) {
                    Glide.with(context)
                        .load(url)
                        .error(defaultRecipeImageErrorResId)
                        .into(binding.recipeImageView)
                } else {
                    binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
                }
            } ?: run {
                binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
            }

            val author = recipe.author
            if (author != null) {
                binding.authorNameText.text = author.nickname ?: "작성자 정보 없음"
                author.profileImageUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        Glide.with(context)
                            .load(url)
                            .placeholder(defaultProfileImagePlaceholderResId)
                            .error(defaultProfileImageErrorResId)
                            .circleCrop()
                            .into(binding.authorProfileImage)
                    } else {
                        binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
                    }
                } ?: run {
                    binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
                }
            } else {
                binding.authorNameText.text = "작성자 미상"
                binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            }

            binding.root.setOnClickListener {
                recipe.id?.let { recipeId ->
                    if (recipeId.isNotEmpty()) {
                        val intent = Intent(context, RecipeDetailActivity::class.java)
                        intent.putExtra("RECIPE_ID", recipeId)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "레시피 정보를 불러올 수 없습니다. (ID 없음)", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Toast.makeText(context, "레시피 정보를 불러올 수 없습니다. (ID null)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class Card03ViewHolder(private val binding: CookCard03Binding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recipe: Recipe) {
            // 기존 Card03 ViewHolder의 bind 로직과 동일하게 구현
            val context = binding.root.context
            binding.recipeNameText.text = recipe.title ?: "제목 없음"
            binding.cookingTimeText.text = recipe.cookingTime?.let { "${it}분" } ?: "시간 정보 없음"
           // recipe.rating?.let { binding.ratingText.text = String.format("%.1f", it) } ?: run { binding.ratingText.text = "N/A" }
            binding.difficultyText.text = recipe.difficulty ?: "정보 없음"

            recipe.thumbnailUrl?.let { url ->
                if (url.isNotEmpty()) {
                    Glide.with(context)
                        .load(url)
                        .error(defaultRecipeImageErrorResId)
                        .into(binding.recipeImageView)
                } else {
                    binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
                }
            } ?: run {
                binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
            }

            val author = recipe.author
            if (author != null) {
                binding.authorNameText.text = author.nickname ?: "작성자 정보 없음"
                author.profileImageUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        Glide.with(context)
                            .load(url)
                            .placeholder(defaultProfileImagePlaceholderResId)
                            .error(defaultProfileImageErrorResId)
                            .circleCrop()
                            .into(binding.authorProfileImage)
                    } else {
                        binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
                    }
                } ?: run {
                    binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
                }
            } else {
                binding.authorNameText.text = "작성자 미상"
                binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            }

            binding.root.setOnClickListener {
                recipe.id?.let { recipeId ->
                    if (recipeId.isNotEmpty()) {
                        val intent = Intent(context, RecipeDetailActivity::class.java)
                        intent.putExtra("RECIPE_ID", recipeId)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "레시피 정보를 불러올 수 없습니다. (ID 없음)", Toast.LENGTH_SHORT).show()
                    }
                } ?: run {
                    Toast.makeText(context, "레시피 정보를 불러올 수 없습니다. (ID null)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    override fun getItemViewType(position: Int): Int {
        return cardLayoutId // 생성자에서 받은 레이아웃 ID를 그대로 반환
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        // viewType (여기서는 cardLayoutId와 동일)에 따라 ViewHolder 생성
        return when (viewType) {
            R.layout.cook_card_01 -> {
                val binding = CookCard01Binding.inflate(inflater, parent, false)
                Card01ViewHolder(binding)
            }
            R.layout.cook_card_02 -> {
                val binding = CookCard02Binding.inflate(inflater, parent, false)
                Card02ViewHolder(binding)
            }
            R.layout.cook_card_03 -> {
                val binding = CookCard03Binding.inflate(inflater, parent, false)
                Card03ViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unsupported layout ID: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val recipe = recipes[position]
        when (holder) {
            is Card01ViewHolder -> holder.bind(recipe)
            is Card02ViewHolder -> holder.bind(recipe)
            is Card03ViewHolder -> holder.bind(recipe)
        }
    }

    override fun getItemCount(): Int = recipes.size
    fun updateRecipes(newRecipes: List<Recipe>) {
        this.recipes = newRecipes
        notifyDataSetChanged()
    }
}