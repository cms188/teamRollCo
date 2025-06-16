package com.example.recipe_pocket

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

sealed class RecipePageItem {
    data class Step(val data: RecipeStep) : RecipePageItem()
    data class FinishPage(val recipe: Recipe) : RecipePageItem()
}

class RecipeStepAdapter(initialRecipe: Recipe?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var pageItems: List<RecipePageItem> = emptyList()

    companion object {
        private const val VIEW_TYPE_STEP = 1
        private const val VIEW_TYPE_FINISH = 2
    }

    init {
        initialRecipe?.let { updateItems(it) }
    }

    private val viewHolders = mutableSetOf<StepViewHolder>()

    override fun getItemViewType(position: Int): Int {
        return when (pageItems[position]) {
            is RecipePageItem.Step -> VIEW_TYPE_STEP
            is RecipePageItem.FinishPage -> VIEW_TYPE_FINISH
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_STEP -> {
                val view = inflater.inflate(R.layout.read_recipe_step, parent, false)
                val holder = StepViewHolder(view)
                viewHolders.add(holder)
                holder
            }
            VIEW_TYPE_FINISH -> {
                val view = inflater.inflate(R.layout.activity_recipe_read_finish, parent, false)
                FinishViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = pageItems[position]) {
            is RecipePageItem.Step -> (holder as StepViewHolder).bind(item.data)
            is RecipePageItem.FinishPage -> (holder as FinishViewHolder).bind(item.recipe)
        }
    }

    override fun getItemCount(): Int = pageItems.size

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is StepViewHolder) {
            holder.releaseCircularTimer()
            viewHolders.remove(holder)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(recipe: Recipe) {
        val newPageItems = mutableListOf<RecipePageItem>()
        recipe.steps?.sortedBy { it.stepNumber }?.forEach { step ->
            newPageItems.add(RecipePageItem.Step(step))
        }
        newPageItems.add(RecipePageItem.FinishPage(recipe))
        this.pageItems = newPageItems
        notifyDataSetChanged()
    }

    fun releaseAllTimers() {
        viewHolders.forEach { it.releaseCircularTimer() }
        viewHolders.clear()
    }

    class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stepTitleTextView: TextView = itemView.findViewById(R.id.tv_step_title)
        private val stepDescriptionTextView: TextView = itemView.findViewById(R.id.tv_step_description)
        private val stepImageView: ImageView = itemView.findViewById(R.id.iv_step_image)
        private val circularTimerView: CircularTimerView = itemView.findViewById(R.id.circular_timer_view)

        fun bind(step: RecipeStep) {
            stepTitleTextView.text = if (step.title.isNullOrEmpty()) "요리하기" else step.title
            stepDescriptionTextView.text = step.description
            if (!step.imageUrl.isNullOrEmpty()) {
                stepImageView.visibility = View.VISIBLE
                Glide.with(itemView.context).load(step.imageUrl)
                    .placeholder(R.drawable.bg_no_img_gray).error(R.drawable.bg_no_img_gray)
                    .into(stepImageView)
            } else {
                stepImageView.visibility = View.GONE
            }
            if (step.useTimer == true && step.time != null && step.time > 0) {
                circularTimerView.visibility = View.VISIBLE
                circularTimerView.setTime(step.time)
            } else {
                circularTimerView.visibility = View.GONE
            }
        }
        fun startTimer() { if (circularTimerView.visibility == View.VISIBLE) circularTimerView.startTimer() }
        fun pauseTimer() { if (circularTimerView.visibility == View.VISIBLE) circularTimerView.pauseTimer() }
        fun releaseCircularTimer() { circularTimerView.releaseTimer() }
    }

    class FinishViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tv_finish_title)
        private val background: ImageView = itemView.findViewById(R.id.iv_recipe_thumbnail)
        private val reviewButton: Button = itemView.findViewById(R.id.btn_leave_review)
        private val bookmarkButton: Button = itemView.findViewById(R.id.btn_add_bookmark)
        private val doneButton: Button = itemView.findViewById(R.id.btn_done)

        fun bind(recipe: Recipe) {
            val context = itemView.context
            title.text = recipe.title ?: "요리 이름"

            Glide.with(context)
                .load(recipe.thumbnailUrl)
                .error(R.drawable.bg_no_img_gray)
                .into(background)

            updateBookmarkButtonUI(recipe.isBookmarked)

            bookmarkButton.setOnClickListener {
                val currentUser = Firebase.auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(context, "로그인이 필요한 기능입니다.", Toast.LENGTH_SHORT).show()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                    return@setOnClickListener
                }

                BookmarkManager.toggleBookmark(recipe.id!!, currentUser.uid, recipe.isBookmarked) { result ->
                    result.onSuccess { newBookmarkState ->
                        recipe.isBookmarked = newBookmarkState
                        updateBookmarkButtonUI(newBookmarkState)
                        val message = if (newBookmarkState) "북마크에 추가되었습니다." else "북마크가 해제되었습니다."
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "북마크 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            doneButton.setOnClickListener {
                (context as? RecipeReadActivity)?.finish()
            }
            reviewButton.setOnClickListener {
                Toast.makeText(context, "후기 남기기 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show()
            }
        }

        private fun updateBookmarkButtonUI(isBookmarked: Boolean) {
            val context = itemView.context
            if (isBookmarked) {
                bookmarkButton.text = "북마크됨"
                val filledIcon = ContextCompat.getDrawable(context, R.drawable.ic_bookmark_filled)
                bookmarkButton.setCompoundDrawablesWithIntrinsicBounds(filledIcon, null, null, null)
                bookmarkButton.compoundDrawables[0]?.setTint(ContextCompat.getColor(context, R.color.orange))

            } else {
                bookmarkButton.text = "북마크에 추가"
                val outlineIcon = ContextCompat.getDrawable(context, R.drawable.ic_bookmark_outline_figma)
                bookmarkButton.setCompoundDrawablesWithIntrinsicBounds(outlineIcon, null, null, null)
                bookmarkButton.compoundDrawables[0]?.setTintList(null)
            }
        }
    }
}