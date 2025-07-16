package com.example.recipe_pocket.ui.recipe.read

import android.content.Context
import android.os.CountDownTimer
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.recipe_pocket.R

class CircularTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var countDownTimer: CountDownTimer? = null

    private var initialTimeInMillis: Long = 0
    private var timeLeftInMillis: Long = 0
    private var isTimerRunning = false

    private val progressBar: ProgressBar
    private val tvTime: TextView
    private val btnPlay: ImageButton
    private val btnPause: ImageButton
    private val btnReset: ImageButton

    private val largeTextSize: Float
    private val smallTextSize: Float

    init {
        LayoutInflater.from(context).inflate(R.layout.view_circular_timer, this, true)
        progressBar = findViewById(R.id.progressBar)
        tvTime = findViewById(R.id.tv_time)
        btnPlay = findViewById(R.id.btn_play)
        btnPause = findViewById(R.id.btn_pause)
        btnReset = findViewById(R.id.btn_reset)

        largeTextSize = tvTime.textSize
        smallTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 36f, resources.displayMetrics
        )

        btnPlay.setOnClickListener {
            if (!isTimerRunning) {
                startTimer()
            }
        }

        btnPause.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            }
        }

        btnReset.setOnClickListener {
            resetTimer()
        }
    }

    fun setTime(totalSeconds: Int) {
        if (totalSeconds <= 0) return
        this.initialTimeInMillis = totalSeconds * 1000L
        resetTimer()
    }

    fun startTimer() {
        if (timeLeftInMillis <= 0 || isTimerRunning) return

        isTimerRunning = true

        countDownTimer = object : CountDownTimer(timeLeftInMillis, 50) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerUI()
            }

            override fun onFinish() {
                timeLeftInMillis = 0
                isTimerRunning = false
                updateTimerUI()
            }
        }.start()
    }
    fun pauseTimer() {
        if (!isTimerRunning) return
        countDownTimer?.cancel()
        isTimerRunning = false
    }

    private fun resetTimer() {
        pauseTimer()
        timeLeftInMillis = initialTimeInMillis
        updateTimerUI()
    }

    private fun updateTimerUI() {
        tvTime.text = formatTime(timeLeftInMillis)
        if (initialTimeInMillis > 0) {
            val progress = (timeLeftInMillis * 10000 / initialTimeInMillis).toInt()
            progressBar.progress = progress
        } else {
            progressBar.progress = 0
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) { // 1시간 이상일 경우 h:m:s 형식으로 표시
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, largeTextSize)
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // pauseTimer()
    }

    fun releaseTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }
}