package com.example.recipe_pocket.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.recipe_pocket.R
import com.example.recipe_pocket.ui.recipe.read.RecipeReadActivity
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingTimerService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var collapsedView: View? = null
    private var expandedView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var countDownTimer: CountDownTimer? = null
    private var initialTimeInMillis: Long = 0
    private var timeLeftInMillis: Long = 0
    private var isTimerRunning = false
    private var recipeId: String? = null
    private var stepPosition: Int = 0

    // UI 요소
    private var collapsedProgressBar: ProgressBar? = null
    private var collapsedTimeText: TextView? = null
    private var expandedProgressBar: ProgressBar? = null
    private var expandedTimeText: TextView? = null
    private var playPauseButton: ImageButton? = null

    // 자동 축소를 위한 핸들러
    private val collapseHandler = Handler(Looper.getMainLooper())
    private val collapseRunnable = Runnable { collapseView() }

    companion object {
        const val ACTION_START = "com.example.recipe_pocket.START_TIMER"
        const val ACTION_STOP = "com.example.recipe_pocket.STOP_TIMER"
        const val EXTRA_TIME_IN_MILLIS = "EXTRA_TIME_IN_MILLIS"
        const val EXTRA_INITIAL_TIME = "EXTRA_INITIAL_TIME"
        const val EXTRA_RECIPE_ID = "EXTRA_RECIPE_ID"
        const val EXTRA_STEP_POSITION = "EXTRA_STEP_POSITION"

        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "FloatingTimerChannel"
        private const val AUTO_COLLAPSE_DELAY = 5000L // 5초
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (floatingView == null) {
                    initialTimeInMillis = intent.getLongExtra(EXTRA_INITIAL_TIME, 0)
                    timeLeftInMillis = intent.getLongExtra(EXTRA_TIME_IN_MILLIS, 0)
                    recipeId = intent.getStringExtra(EXTRA_RECIPE_ID)
                    stepPosition = intent.getIntExtra(EXTRA_STEP_POSITION, 0)
                    showFloatingWidget()
                    startTimer(timeLeftInMillis)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification())
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        if (floatingView != null) windowManager.removeView(floatingView)
    }

    private fun showFloatingWidget() {
        val themedContext = ContextThemeWrapper(this, applicationInfo.theme)
        val inflater = themedContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        floatingView = inflater.inflate(R.layout.layout_floating_timer, null)
        collapsedView = floatingView?.findViewById(R.id.collapsed_view)
        expandedView = floatingView?.findViewById(R.id.expanded_view)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        windowManager.addView(floatingView, params)
        setupUIListeners()
    }

    private fun setupUIListeners() {
        // UI 요소 초기화
        collapsedProgressBar = collapsedView?.findViewById(R.id.collapsed_progressBar)
        collapsedTimeText = collapsedView?.findViewById(R.id.collapsed_time_text)
        expandedProgressBar = expandedView?.findViewById(R.id.expanded_progressBar)
        expandedTimeText = expandedView?.findViewById(R.id.expanded_time_text)
        playPauseButton = expandedView?.findViewById(R.id.btn_play_pause)

        val closeButton = expandedView?.findViewById<ImageButton>(R.id.btn_close)
        val maximizeButton = expandedView?.findViewById<ImageButton>(R.id.btn_maximize)

        closeButton?.setOnClickListener { stopSelf() }
        maximizeButton?.setOnClickListener { openApp() }
        playPauseButton?.setOnClickListener { toggleTimer() }

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private val CLICK_DRAG_TOLERANCE = 10f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (expandedView?.visibility == View.VISIBLE) {
                        collapseView()
                    }
                    return false
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val xDiff = abs(event.rawX - initialTouchX)
                        val yDiff = abs(event.rawY - initialTouchY)
                        if (xDiff < CLICK_DRAG_TOLERANCE && yDiff < CLICK_DRAG_TOLERANCE) {
                            if (collapsedView?.visibility == View.VISIBLE) {
                                expandView()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun expandView() {
        collapsedView?.visibility = View.GONE
        expandedView?.visibility = View.VISIBLE
        collapseHandler.postDelayed(collapseRunnable, AUTO_COLLAPSE_DELAY)
    }

    private fun collapseView() {
        collapsedView?.visibility = View.VISIBLE
        expandedView?.visibility = View.GONE
    }

    private fun startTimer(time: Long) {
        isTimerRunning = true
        updatePlayPauseButton()
        countDownTimer = object : CountDownTimer(time, 50) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerUI()
            }
            override fun onFinish() {
                timeLeftInMillis = 0
                isTimerRunning = false
                updateTimerUI()
                updatePlayPauseButton()
            }
        }.start()
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        isTimerRunning = false
        updatePlayPauseButton()
    }

    private fun toggleTimer() {
        if (isTimerRunning) {
            pauseTimer()
        } else {
            if (timeLeftInMillis > 0) {
                startTimer(timeLeftInMillis)
            }
        }
        collapseHandler.removeCallbacks(collapseRunnable)
        collapseHandler.postDelayed(collapseRunnable, AUTO_COLLAPSE_DELAY)
    }


    private fun updateTimerUI() {
        val formattedTime = formatTime(timeLeftInMillis)
        collapsedTimeText?.text = formattedTime
        expandedTimeText?.text = formattedTime

        val progress = if (initialTimeInMillis > 0) {
            (timeLeftInMillis.toDouble() * 10000 / initialTimeInMillis).roundToInt()
        } else {
            0
        }

        collapsedProgressBar?.progress = progress
        expandedProgressBar?.progress = progress
    }

    private fun updatePlayPauseButton() {
        if (isTimerRunning) {
            playPauseButton?.setImageResource(R.drawable.ic_pause)
        } else {
            playPauseButton?.setImageResource(R.drawable.ic_play)
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun openApp() {
        val intent = Intent(this, RecipeReadActivity::class.java).apply {
            putExtra("RECIPE_ID", recipeId)
            putExtra(RecipeReadActivity.EXTRA_TARGET_STEP, stepPosition)
            putExtra(RecipeReadActivity.EXTRA_REMAINING_TIME, timeLeftInMillis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(RecipeReadActivity.EXTRA_FROM_FLOATING_TIMER, true)
        }
        startActivity(intent)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "플로팅 타이머", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            CHANNEL_ID
        } else {
            ""
        }

        val notificationIntent = Intent(this, RecipeReadActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("요리 타이머 작동 중")
            .setContentText("타이머가 화면에 표시되고 있습니다.")
            .setSmallIcon(R.drawable.ic_clock)
            .setContentIntent(pendingIntent)
            .build()
    }
}