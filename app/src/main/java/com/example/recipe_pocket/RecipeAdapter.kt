package com.example.recipe_pocket

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.CookCard01Binding
import com.example.recipe_pocket.databinding.CookCard02Binding
import com.example.recipe_pocket.databinding.CookCard03Binding
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.recipe.read.RecipeDetailActivity
import com.example.recipe_pocket.ui.user.bookmark.BookmarkManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.BuildConfig
import com.google.firebase.ktx.Firebase

class RecipeAdapter(
    private var recipes: List<Recipe>,
    @LayoutRes private val cardLayoutId: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val defaultRecipeImageErrorResId: Int = R.drawable.bg_no_img_gray
    private val defaultProfileImagePlaceholderResId: Int = R.drawable.bg_main_circle_gray
    private val defaultProfileImageErrorResId: Int = R.drawable.bg_no_img_gray

    private fun updateBookmarkIcon(button: ImageButton, isBookmarked: Boolean) {
        val context = button.context
        if (isBookmarked) {
            button.setImageResource(R.drawable.ic_bookmark_filled)
            button.setColorFilter(ContextCompat.getColor(context, R.color.orange))
        } else {
            button.setImageResource(R.drawable.ic_bookmark_outline_figma)
            button.setColorFilter(ContextCompat.getColor(context, R.color.black))
        }
    }

    abstract inner class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(recipe: Recipe)
    }

    inner class Card01ViewHolder(private val binding: CookCard01Binding) : BaseViewHolder(binding.root) {
        override fun bind(recipe: Recipe) {
            val context = binding.root.context
            binding.recipeNameText.text = recipe.title ?: "제목 없음"
            binding.cookingTimeText.text = recipe.cookingTime?.let { "${it}분" } ?: "시간 정보 없음"

            recipe.thumbnailUrl?.let { url ->
                if (url.isNotEmpty()) {
                    Glide.with(context).load(url).error(defaultRecipeImageErrorResId).into(binding.recipeImageView)
                } else {
                    binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
                }
            } ?: binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)

            val author = recipe.author
            if (author != null) {
                binding.authorNameText.text = author.nickname ?: "작성자 정보 없음"
                author.profileImageUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                    Glide.with(context).load(url).placeholder(defaultProfileImagePlaceholderResId)
                        .error(defaultProfileImageErrorResId).circleCrop().into(binding.authorProfileImage)
                } ?: binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            } else {
                binding.authorNameText.text = "작성자 미상"
                binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            }

            updateBookmarkIcon(binding.bookmarkButton, recipe.isBookmarked)

            binding.bookmarkButton.setOnClickListener {
                val currentUser = Firebase.auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(context, "로그인이 필요한 기능입니다.", Toast.LENGTH_SHORT).show()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    return@setOnClickListener
                }

                BookmarkManager.toggleBookmark(recipe.id!!, currentUser.uid, recipe.isBookmarked) { result ->
                    result.onSuccess { newBookmarkState ->
                        recipe.isBookmarked = newBookmarkState
                        updateBookmarkIcon(binding.bookmarkButton, newBookmarkState)
                    }.onFailure {
                        Toast.makeText(context, "북마크 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.root.setOnClickListener {
                recipe.id?.let { recipeId ->
                    val intent = Intent(context, RecipeDetailActivity::class.java)
                    intent.putExtra("RECIPE_ID", recipeId)
                    context.startActivity(intent)
                }
            }
        }
    }

    inner class Card02ViewHolder(private val binding: CookCard02Binding) : BaseViewHolder(binding.root) {
        override fun bind(recipe: Recipe) {
            val context = binding.root.context
            binding.recipeNameText.text = recipe.title ?: "제목 없음"
            binding.cookingTimeText.text = recipe.cookingTime?.let { "${it}분" } ?: "시간 정보 없음"

            recipe.thumbnailUrl?.let { url ->
                if (url.isNotEmpty()) {
                    Glide.with(context).load(url).error(defaultRecipeImageErrorResId).into(binding.recipeImageView)
                } else {
                    binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
                }
            } ?: binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)

            val author = recipe.author
            if (author != null) {
                binding.authorNameText.text = author.nickname ?: "작성자 정보 없음"
                author.profileImageUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                    Glide.with(context).load(url).placeholder(defaultProfileImagePlaceholderResId)
                        .error(defaultProfileImageErrorResId).circleCrop().into(binding.authorProfileImage)
                } ?: binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            } else {
                binding.authorNameText.text = "작성자 미상"
                binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            }

            updateBookmarkIcon(binding.bookmarkButton, recipe.isBookmarked)

            binding.bookmarkButton.setOnClickListener {
                val currentUser = Firebase.auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(context, "로그인이 필요한 기능입니다.", Toast.LENGTH_SHORT).show()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    return@setOnClickListener
                }

                BookmarkManager.toggleBookmark(recipe.id!!, currentUser.uid, recipe.isBookmarked) { result ->
                    result.onSuccess { newBookmarkState ->
                        recipe.isBookmarked = newBookmarkState
                        updateBookmarkIcon(binding.bookmarkButton, newBookmarkState)
                    }.onFailure {
                        Toast.makeText(context, "북마크 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.root.setOnClickListener {
                recipe.id?.let { recipeId ->
                    val intent = Intent(context, RecipeDetailActivity::class.java)
                    intent.putExtra("RECIPE_ID", recipeId)
                    context.startActivity(intent)
                }
            }
        }
    }

    inner class Card03ViewHolder(private val binding: CookCard03Binding) : BaseViewHolder(binding.root) {
        override fun bind(recipe: Recipe) {
            val context = binding.root.context
            binding.recipeNameText.text = recipe.title ?: "제목 없음"
            binding.cookingTimeText.text = recipe.cookingTime?.let { "${it}분" } ?: "시간 정보 없음"
            binding.difficultyText.text = recipe.difficulty ?: "정보 없음"

            // 평점, 리뷰 수, 좋아요 수를 실제 데이터로 바인딩
            binding.ratingText.text = String.format("%.1f", recipe.averageRating)
            binding.reviewCountText.text = "(${recipe.reviewCount})"
            binding.likeCountText.text = (recipe.likeCount ?: 0).toString()

            recipe.thumbnailUrl?.let { url ->
                if (url.isNotEmpty()) {
                    Glide.with(context).load(url).error(defaultRecipeImageErrorResId).into(binding.recipeImageView)
                } else {
                    binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)
                }
            } ?: binding.recipeImageView.setImageResource(defaultRecipeImageErrorResId)

            val author = recipe.author
            if (author != null) {
                if (!author.title.isNullOrEmpty()) {
                    binding.authorTitleText.visibility = View.VISIBLE
                    binding.authorTitleText.text = author.title
                } else {
                    binding.authorTitleText.visibility = View.GONE
                }
                binding.authorNameText.text = author.nickname ?: "작성자 정보 없음"
                author.profileImageUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                    Glide.with(context).load(url).placeholder(defaultProfileImagePlaceholderResId)
                        .error(defaultProfileImageErrorResId).circleCrop().into(binding.authorProfileImage)
                } ?: binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            } else {
                binding.authorNameText.text = "작성자 미상"
                binding.authorProfileImage.setImageResource(defaultProfileImageErrorResId)
            }

            updateBookmarkIcon(binding.bookmarkButton, recipe.isBookmarked)

            binding.bookmarkButton.setOnClickListener {
                val currentUser = Firebase.auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(context, "로그인이 필요한 기능입니다.", Toast.LENGTH_SHORT).show()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    return@setOnClickListener
                }

                BookmarkManager.toggleBookmark(recipe.id!!, currentUser.uid, recipe.isBookmarked) { result ->
                    result.onSuccess { newBookmarkState ->
                        recipe.isBookmarked = newBookmarkState
                        updateBookmarkIcon(binding.bookmarkButton, newBookmarkState)
                    }.onFailure {
                        Toast.makeText(context, "북마크 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            binding.root.setOnClickListener {
                recipe.id?.let { recipeId ->
                    val intent = Intent(context, RecipeDetailActivity::class.java)
                    intent.putExtra("RECIPE_ID", recipeId)
                    context.startActivity(intent)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return cardLayoutId
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.cook_card_01 -> Card01ViewHolder(CookCard01Binding.inflate(inflater, parent, false))
            R.layout.cook_card_02 -> Card02ViewHolder(CookCard02Binding.inflate(inflater, parent, false))
            R.layout.cook_card_03 -> Card03ViewHolder(CookCard03Binding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Unsupported layout ID: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as BaseViewHolder).bind(recipes[position])
    }

    override fun getItemCount(): Int = recipes.size

    fun updateRecipes(newRecipes: List<Recipe>) {
        this.recipes = newRecipes
        notifyDataSetChanged()
    }
}