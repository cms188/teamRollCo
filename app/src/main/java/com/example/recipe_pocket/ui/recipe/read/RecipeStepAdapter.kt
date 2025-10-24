package com.example.recipe_pocket.ui.recipe.read

import android.annotation.SuppressLint
import android.content.Intent
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.data.RecipeStep
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.review.ReviewWriteActivity
import com.example.recipe_pocket.ui.user.bookmark.BookmarkManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

sealed class RecipePageItem {
    data class Step(val data: RecipeStep) : RecipePageItem()
    data class FinishPage(val recipe: Recipe) : RecipePageItem()
}

class RecipeStepAdapter(
    initialRecipe: Recipe?,
    private val timerStateListener: OnTimerStateChangedListener? // 리스너 추가
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var pageItems: List<RecipePageItem> = emptyList()

    // Activity에 타이머 상태를 알리기 위한 인터페이스
    interface OnTimerStateChangedListener {
        fun onTimerStart()
        fun onTimerPause()
        fun onTimerStop()
    }

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
                val holder = StepViewHolder(view, timerStateListener) // ViewHolder 생성 시 리스너 전달
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
            holder.releaseTimer()
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
        viewHolders.forEach { it.releaseTimer() }
        viewHolders.clear()
    }

    class StepViewHolder(itemView: View, private val listener: OnTimerStateChangedListener?) : RecyclerView.ViewHolder(itemView) {
        private val stepTitleTextView: TextView = itemView.findViewById(R.id.tv_step_title)
        private val stepDescriptionTextView: TextView = itemView.findViewById(R.id.tv_step_description)
        private val stepImageView: ImageView = itemView.findViewById(R.id.iv_step_image)

        // 타이머 관련 UI 요소
        private val timerContainer: LinearLayout = itemView.findViewById(R.id.timer_container)
        private val timerTimeTextView: TextView = itemView.findViewById(R.id.tv_timer_time)
        private val timerProgressBar: ProgressBar = itemView.findViewById(R.id.pb_timer_progress)
        private val playPauseButton: ImageButton = itemView.findViewById(R.id.btn_timer_play_pause)
        private val resetButton: ImageButton = itemView.findViewById(R.id.btn_timer_reset)

        private var countDownTimer: CountDownTimer? = null
        private var initialTimeInMillis: Long = 0
        private var timeLeftInMillis: Long = 0
        private var isRunning = false

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
                timerContainer.visibility = View.VISIBLE
                initialTimeInMillis = step.time * 1000L
                timeLeftInMillis = initialTimeInMillis
                updateTimerUI()
            } else {
                timerContainer.visibility = View.GONE
            }

            playPauseButton.setOnClickListener {
                if (isRunning) pauseTimer() else startTimer()
            }

            resetButton.setOnClickListener {
                resetTimer()
            }
        }

        fun startTimer() {
            if (timeLeftInMillis <= 0 || isRunning) return
            isRunning = true
            updatePlayPauseButton()
            listener?.onTimerStart()

            countDownTimer = object : CountDownTimer(timeLeftInMillis, 50) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMillis = millisUntilFinished
                    updateTimerUI()
                }

                override fun onFinish() {
                    timeLeftInMillis = 0
                    isRunning = false
                    updateTimerUI()
                    updatePlayPauseButton()
                    listener?.onTimerStop()
                }
            }.start()
        }

        fun pauseTimer() {
            if (!isRunning) return
            countDownTimer?.cancel()
            isRunning = false
            updatePlayPauseButton()
            listener?.onTimerPause()
        }

        private fun resetTimer() {
            val wasRunning = isRunning
            pauseTimer()
            timeLeftInMillis = initialTimeInMillis
            updateTimerUI()
            updatePlayPauseButton()
            if (wasRunning) {
                listener?.onTimerStop()
            }
        }

        private fun updateTimerUI() {
            val totalSeconds = (timeLeftInMillis + 999) / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            timerTimeTextView.text = String.format("%02d:%02d", minutes, seconds)

            val progress = if (initialTimeInMillis > 0) {
                (timeLeftInMillis.toDouble() * 1000 / initialTimeInMillis).toInt()
            } else {
                0
            }
            timerProgressBar.progress = progress
        }

        private fun updatePlayPauseButton() {
            if (isRunning) {
                playPauseButton.setImageResource(R.drawable.ic_pause)
            } else {
                playPauseButton.setImageResource(R.drawable.ic_play)
            }
        }

        fun releaseTimer() {
            countDownTimer?.cancel()
            countDownTimer = null
        }

        fun getTimeLeftInMillis(): Long = timeLeftInMillis
        fun getInitialTimeInMillis(): Long = initialTimeInMillis
        fun isTimerRunning(): Boolean = isRunning
        fun setRemainingTime(remainingMillis: Long) {
            if (!isRunning) {
                timeLeftInMillis = remainingMillis
                updateTimerUI()
            }
        }
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

            val currentUser = Firebase.auth.currentUser
            updateBookmarkButtonUI(recipe.isBookmarked)

            reviewButton.setOnClickListener {
                if (currentUser != null && currentUser.uid == recipe.userId) {
                    Toast.makeText(context, "자신의 레시피엔 후기를 남길 수 없습니다.", Toast.LENGTH_SHORT).show()
                } else if (currentUser == null) {
                    Toast.makeText(context, "로그인이 필요한 기능입니다.", Toast.LENGTH_SHORT).show()
                    context.startActivity(Intent(context, LoginActivity::class.java))
                } else {
                    val intent = Intent(context, ReviewWriteActivity::class.java).apply {
                        putExtra("RECIPE_ID", recipe.id)
                    }
                    (context as? AppCompatActivity)?.startActivity(intent)
                }
            }

            bookmarkButton.setOnClickListener {
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