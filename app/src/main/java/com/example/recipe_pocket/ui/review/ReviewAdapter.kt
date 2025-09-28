package com.example.recipe_pocket.ui.review

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.Review
import com.example.recipe_pocket.databinding.CardRecipeReviewBinding
import java.text.SimpleDateFormat
import java.util.*

class ReviewAdapter(
    private var reviews: List<Review>,
    private val showRecipeTitle: Boolean = true,
    private val onItemClick: ((Review) -> Unit)? = null,
    private val onItemLongClick: ((Review) -> Unit)? = null
) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    inner class ReviewViewHolder(private val binding: CardRecipeReviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(review: Review) {
            binding.textViewAuthorName.text = review.userNickname ?: "사용자"
            //binding.textViewRatingScore.text = String.format("%.1f", review.rating)
            binding.textViewReviewComent.text = review.comment

            if (!review.userProfileUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context).load(review.userProfileUrl).into(binding.imageViewProfile)
            } else {
                binding.imageViewProfile.setImageResource(R.drawable.ic_profile_placeholder)
            }

            review.createdAt?.toDate()?.let {
                val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN)
                binding.textViewReviewDate.text = sdf.format(it)
            }

            setRatingStars(review.rating)

            if (showRecipeTitle && !review.recipeTitle.isNullOrEmpty()) {
                binding.tvRecipeTitleLabel.visibility = View.VISIBLE
                binding.tvRecipeTitleValue.visibility = View.VISIBLE
                binding.tvRecipeTitleValue.text = review.recipeTitle
            } else {
                binding.tvRecipeTitleLabel.visibility = View.GONE
                binding.tvRecipeTitleValue.visibility = View.GONE
            }

            onItemClick?.let {
                itemView.setOnClickListener { it(review) }
            }

            onItemLongClick?.let {
                itemView.setOnLongClickListener {
                    it(review)
                    true
                }
            }
        }

        private fun setRatingStars(rating: Float) {
            val stars = listOf(binding.star1, binding.star2, binding.star3, binding.star4, binding.star5)
            for (i in stars.indices) {
                when {
                    rating >= i + 1 -> stars[i].setImageResource(R.drawable.ic_star_fill)
                    rating >= i + 0.5 -> stars[i].setImageResource(R.drawable.ic_star_half)
                    else -> stars[i].setImageResource(R.drawable.ic_star_empty)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = CardRecipeReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(reviews[position])
    }

    override fun getItemCount(): Int = reviews.size

    fun updateReviews(newReviews: List<Review>) {
        this.reviews = newReviews
        notifyDataSetChanged()
    }
}