package com.example.recipe_pocket.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.recipe_pocket.R
import com.example.recipe_pocket.ui.recipe.read.RecipeReadActivity
import kotlin.collections.iterator

class VoiceRecognitionService : Service() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private var isListening = false
    private var originalNotificationVolume: Int = -1
    private lateinit var audioManager: AudioManager
    private var isStoppingSelf = false // 서비스 종료 플래그

    companion object {
        const val CHANNEL_ID = "VoiceRecognitionChannel"
        const val NOTIFICATION_ID = 1
        const val TAG = "VoiceRecService"

        const val ACTION_VOICE_COMMAND = "com.example.recipe_pocket.VOICE_COMMAND_SERVICE"
        const val EXTRA_COMMAND = "extra_command"

        const val COMMAND_NEXT = "NEXT"
        const val COMMAND_PREVIOUS = "PREVIOUS"
        const val COMMAND_STOP_RECOGNITION_FROM_VOICE = "STOP_RECOGNITION_FROM_VOICE"
        const val COMMAND_SERVICE_STOPPED_UNEXPECTEDLY = "SERVICE_STOPPED_UNEXPECTEDLY"
        const val COMMAND_TIMER_START = "TIMER_START"
        const val COMMAND_TIMER_PAUSE = "TIMER_PAUSE"

        const val ACTION_START_RECOGNITION = "ACTION_START_RECOGNITION_SERVICE"
        const val ACTION_STOP_RECOGNITION = "ACTION_STOP_RECOGNITION_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate - Service created")
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

        if (!checkPermission()) {
            sendServiceStatusBroadcast(COMMAND_SERVICE_STOPPED_UNEXPECTEDLY, "권한 없음")
            stopSelf()
            return
        }
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - Action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_RECOGNITION -> {
                startForeground(NOTIFICATION_ID, createNotification())
                if (!isListening) {
                    startVoiceRecognition()
                }
            }
            ACTION_STOP_RECOGNITION -> {
                stopVoiceRecognition()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy - Service being destroyed")
        isStoppingSelf = true
        stopVoiceRecognition()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        restoreNotificationSound()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "음성 인식 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, RecipeReadActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("음성 인식 활성화됨")
            .setContentText("요리 도우미가 듣고 있습니다.")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun initSpeechRecognizer() {
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        Log.d(TAG, "SpeechRecognizer initialized")
    }

    private fun startVoiceRecognition() {
        if (!::speechRecognizer.isInitialized) initSpeechRecognizer()
        if (!isListening && checkPermission()) {
            muteNotificationSound()
            speechRecognizer.startListening(recognizerIntent)
            isListening = true
            Log.d(TAG, "Voice recognition started.")
        } else if (!checkPermission()) {
            sendServiceStatusBroadcast(COMMAND_SERVICE_STOPPED_UNEXPECTEDLY, "권한 없음 startVR")
            stopSelf()
        }
    }

    private fun stopVoiceRecognition() {
        if (::speechRecognizer.isInitialized && isListening) {
            isListening = false // stopListening보다 먼저 호출하여 재시작 방지
            speechRecognizer.stopListening()
            Log.d(TAG, "Voice recognition stopped.")
        }
        restoreNotificationSound()
    }

    private val recognitionListener: RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { Log.d(TAG, "onReadyForSpeech") }
        override fun onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech") }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            isListening = false
            // 서비스 종료 요청이 없을 때만 재시작
            if (!isStoppingSelf) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isStoppingSelf) startVoiceRecognition()
                }, 100)
            }
        }

        override fun onError(error: Int) {
            val errorMessage = when(error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "일치하는 항목 없음"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말하는 시간 초과"
                SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러"
                else -> "오류 코드: $error"
            }
            Log.e(TAG, "onError: $errorMessage")
            isListening = false
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                sendServiceStatusBroadcast(COMMAND_SERVICE_STOPPED_UNEXPECTEDLY, "권한 에러")
                stopSelf()
                return
            }
            if (!isStoppingSelf) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isStoppingSelf) startVoiceRecognition()
                }, 500)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches != null && matches.isNotEmpty()) {
                val recognizedText = matches[0]
                Log.d(TAG, "onResults: $recognizedText")
                processText(recognizedText)?.let { command ->
                    sendCommandToActivity(command)
                    if (command == COMMAND_STOP_RECOGNITION_FROM_VOICE) {
                        stopVoiceRecognition()
                        stopSelf()
                    }
                }
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun processText(text: String): String? {
        val lowerText = text.lowercase()
        val commandKeywords = mapOf(
            listOf("이전", "이전으로", "이전단계", "이전 단계") to COMMAND_PREVIOUS,
            listOf("다음", "다음으로", "다음단계", "다음 단계") to COMMAND_NEXT,
            listOf("종료", "음성인식 종료", "그만") to COMMAND_STOP_RECOGNITION_FROM_VOICE,
            listOf("타이머 시작", "타이머 재생", "시작", "재생") to COMMAND_TIMER_START,
            listOf("타이머 정지", "타이머 일시정지", "타이머 일시 정지", "타이머 멈춰", "멈춰", "정지") to COMMAND_TIMER_PAUSE
        )
        for ((keywords, command) in commandKeywords) {
            if (keywords.any { lowerText.contains(it) }) return command
        }
        return null
    }

    private fun sendCommandToActivity(command: String) {
        val intent = Intent(ACTION_VOICE_COMMAND).putExtra(EXTRA_COMMAND, command)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendServiceStatusBroadcast(command: String, message: String? = null) {
        val intent = Intent(ACTION_VOICE_COMMAND).apply {
            putExtra(EXTRA_COMMAND, command)
            message?.let { putExtra("message", it) }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun muteNotificationSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            } catch (e: Exception) { Log.e(TAG, "Error muting notification", e) }
        }
    }

    private fun restoreNotificationSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && originalNotificationVolume != -1) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0)
                originalNotificationVolume = -1
            } catch (e: Exception) { Log.e(TAG, "Error restoring notification", e) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}