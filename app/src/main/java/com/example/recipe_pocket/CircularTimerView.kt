// CircularTimerView.kt (수정된 전체 코드)
package com.example.recipe_pocket

import android.content.Context
import android.os.CountDownTimer
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

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
    private val btnPlayPause: ImageButton
    private val btnReset: ImageButton

    private val largeTextSize: Float
    private val smallTextSize: Float

    init {
        LayoutInflater.from(context).inflate(R.layout.view_circular_timer, this, true)
        progressBar = findViewById(R.id.progressBar)
        tvTime = findViewById(R.id.tv_time)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnReset = findViewById(R.id.btn_reset)

        // XML에 정의된 텍스트 크기를 기준으로 px 값 가져오기
        largeTextSize = tvTime.textSize
        // 작은 텍스트 크기를 sp 단위로 정의하고 px로 변환 (예: 36sp)
        smallTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 36f, resources.displayMetrics
        )


        btnPlayPause.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        btnReset.setOnClickListener {
            resetTimer()
        }
    }

    fun setTime(minutes: Int) {
        if (minutes <= 0) return
        this.initialTimeInMillis = minutes * 60 * 1000L
        resetTimer()
    }

    private fun startTimer() {
        if (timeLeftInMillis <= 0) return

        isTimerRunning = true
        updateButtonUI()

        countDownTimer = object : CountDownTimer(timeLeftInMillis, 50) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerUI()
            }

            override fun onFinish() {
                timeLeftInMillis = 0
                isTimerRunning = false
                updateTimerUI()
                updateButtonUI()
            }
        }.start()
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        updateButtonUI()
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

    private fun updateButtonUI() {
        if (isTimerRunning) {
            btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (totalSeconds > 5940) {
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallTextSize)
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, largeTextSize)
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // pauseTimer() // 다음 단계로 넘어가면 타이머 멈추게 하는 기능 일단 보류
    }

    fun releaseTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
    }
}