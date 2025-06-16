package com.example.recipe_pocket

import android.Manifest
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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class VoiceRecognitionService : Service() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private var isListening = false
    private var originalNotificationVolume: Int = -1 // 알림음 복구용
    private lateinit var audioManager: AudioManager


    companion object {
        const val TAG = "VoiceRecService"
        const val ACTION_VOICE_COMMAND = "com.example.recipe_pocket.VOICE_COMMAND_SERVICE" // 액션명 변경 (Activity와 구분)
        const val EXTRA_COMMAND = "extra_command"

        const val COMMAND_NEXT = "NEXT"
        const val COMMAND_PREVIOUS = "PREVIOUS"
        const val COMMAND_STOP_RECOGNITION_FROM_VOICE = "STOP_RECOGNITION_FROM_VOICE"
        const val COMMAND_SERVICE_STOPPED_UNEXPECTEDLY = "SERVICE_STOPPED_UNEXPECTEDLY" // 예기치 않은 종료 알림

        // ▼▼▼ 타이머 제어 명령어 추가 ▼▼▼
        const val COMMAND_TIMER_START = "TIMER_START"
        const val COMMAND_TIMER_PAUSE = "TIMER_PAUSE"

        // RecipeReadActivity에서 서비스 제어를 위한 Action
        const val ACTION_START_RECOGNITION = "ACTION_START_RECOGNITION_SERVICE"
        const val ACTION_STOP_RECOGNITION = "ACTION_STOP_RECOGNITION_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate - Service created")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (!checkPermission()) {
            Log.e(TAG, "Audio permission not granted. Stopping service.")
            sendServiceStatusBroadcast(COMMAND_SERVICE_STOPPED_UNEXPECTEDLY, "권한 없음")
            stopSelf() // 권한 없으면 서비스 스스로 종료
            return
        }
        initSpeechRecognizer()
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun initSpeechRecognizer() {
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(recognitionListener)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false) // 부분 결과는 일단 false
            // EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS 와 같은 옵션으로 타임아웃 조절 가능
        }
        Log.d(TAG, "SpeechRecognizer initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - Action: ${intent?.action}")
        if (!checkPermission()) { // 서비스 시작 시점에 다시 한번 권한 확인
            Log.e(TAG, "Audio permission not granted at onStartCommand. Stopping service.")
            sendServiceStatusBroadcast(COMMAND_SERVICE_STOPPED_UNEXPECTEDLY, "권한 없음 aoS")
            stopSelf()
            return START_NOT_STICKY
        }


        when (intent?.action) {
            ACTION_START_RECOGNITION -> {
                if (!isListening) {
                    startVoiceRecognition()
                } else {
                    Log.d(TAG, "Already listening, ignoring START_RECOGNITION.")
                }
            }
            ACTION_STOP_RECOGNITION -> {
                stopVoiceRecognition()
                stopSelf() // 서비스 종료
            }
        }
        return START_STICKY // 서비스가 시스템에 의해 종료되면 재시작 (상황에 따라 START_NOT_STICKY 고려)
    }

    private fun startVoiceRecognition() {
        if (!::speechRecognizer.isInitialized) { // 안전장치
            initSpeechRecognizer()
        }
        if (!isListening && checkPermission()) { // 다시 한번 권한 체크
            muteNotificationSound()
            speechRecognizer.startListening(recognizerIntent)
            isListening = true
            Log.d(TAG, "Voice recognition started by Service.")
            // Toast.makeText(this, "음성인식 서비스 시작됨", Toast.LENGTH_SHORT).show() // 서비스에서는 UI 상호작용 지양
        } else if (!checkPermission()) {
            Log.e(TAG, "Permission denied, cannot start recognition.")
            sendServiceStatusBroadcast(COMMAND_SERVICE_STOPPED_UNEXPECTEDLY, "권한 없음 startVR")
            stopSelf()
        }
    }

    private fun stopVoiceRecognition() {
        if (::speechRecognizer.isInitialized && isListening) {
            speechRecognizer.stopListening()
            isListening = false
            Log.d(TAG, "Voice recognition stopped by Service.")
        }
        restoreNotificationSound()
    }

    private fun muteNotificationSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // M 이상에서만 알림음 제어 시도
            try {
                originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                Log.d(TAG, "Notification sound muted.")
            } catch (e: Exception) {
                Log.e(TAG, "Error muting notification sound", e)
            }
        }
    }

    private fun restoreNotificationSound() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && originalNotificationVolume != -1) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0)
                Log.d(TAG, "Notification sound restored.")
                originalNotificationVolume = -1 // 복구 후 초기화
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring notification sound", e)
            }
        }
    }

    private val recognitionListener: RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            isListening = false // 중요: 여기서 isListening을 false로 설정해야 다음 startListening이 제대로 동작
            // 계속 듣기 (서비스가 명시적으로 중단되기 전까지)
            if (SpeechRecognizer.isRecognitionAvailable(applicationContext) && !isStoppingSelf) { // 서비스가 중단 요청된 상태가 아닐 때만 재시작
                Handler(Looper.getMainLooper()).postDelayed({ // 짧은 딜레이 후 재시작
                    if (!isStoppingSelf) startVoiceRecognition() // stopSelf() 호출 상태가 아닐 때만
                }, 100) // 너무 짧으면 문제 생길 수 있음, 적절히 조절
            }
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "오디오 에러"
                SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러" // 종종 발생, 무시하고 재시작
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "퍼미션 없음"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃"
                SpeechRecognizer.ERROR_NO_MATCH -> "일치하는 항목 없음" // 재시작
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer 바쁨" // 재시작 시도
                SpeechRecognizer.ERROR_SERVER -> "서버 오류"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말하는 시간 초과" // 재시작
                else -> "알 수 없는 오류 ($error)"
            }
            Log.e(TAG, "onError: $message")
            isListening = false // 에러 시 리스닝 상태 해제

            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                sendServiceStatusBroadcast(COMMAND_SERVICE_STOPPED_UNEXPECTEDLY, "권한 에러로 서비스 중지")
                stopSelf() // 권한 없으면 서비스 종료
                return
            }

            // 특정 에러(ERROR_CLIENT, ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT, ERROR_RECOGNIZER_BUSY 등) 발생 시 재시작
            // 너무 잦은 재시작을 막기 위해 카운터나 딜레이 조절 필요할 수 있음
            if (SpeechRecognizer.isRecognitionAvailable(applicationContext) && !isStoppingSelf) {
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

                val command = processText(recognizedText)
                if (command != null) {
                    sendCommandToActivity(command)
                    if (command == COMMAND_STOP_RECOGNITION_FROM_VOICE) {
                        stopVoiceRecognition() // 리스너 중지
                        stopSelf() // 서비스 종료
                    }
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private var isStoppingSelf = false // stopSelf() 호출 여부 플래그

    override fun onDestroy() {
        Log.d(TAG, "onDestroy - Service being destroyed")
        isStoppingSelf = true // 파괴 과정임을 명시
        stopVoiceRecognition() // 리스너 정리
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
            Log.d(TAG, "SpeechRecognizer destroyed in Service.")
        }
        restoreNotificationSound() // 혹시 모를 복구
        super.onDestroy()
    }

    private fun processText(text: String): String? {
        val lowerText = text.lowercase()

        val commandKeywords = mapOf(
            listOf("이전", "이전으로", "이전단계", "이전 단계") to COMMAND_PREVIOUS,
            listOf("다음", "다음으로", "다음단계", "다음 단계") to COMMAND_NEXT,
            listOf("종료", "음성인식 종료", "그만") to COMMAND_STOP_RECOGNITION_FROM_VOICE,

            listOf("타이머 시작", "타이머 재생", "타이머 시작해줘", "타이머 재생해줘") to COMMAND_TIMER_START,
            listOf("타이머 정지", "타이머 일시정지", "타이머 일시 정지", "타이머 멈춰줘", "타이머 멈춰", "타이머 정지해줘") to COMMAND_TIMER_PAUSE
        )

        for ((keywords, command) in commandKeywords) {
            for (keyword in keywords) {
                if (lowerText.contains(keyword)) {
                    Log.d(TAG, "Keyword '$keyword' (from '$lowerText') detected for command '$command'")
                    return command
                }
            }
        }
        return null
    }

    private fun sendCommandToActivity(command: String) {
        Log.d(TAG, "Sending command to Activity: $command")
        val intent = Intent(ACTION_VOICE_COMMAND).apply {
            putExtra(EXTRA_COMMAND, command)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendServiceStatusBroadcast(command: String, message: String? = null) {
        Log.d(TAG, "Sending service status to Activity: $command, Message: $message")
        val intent = Intent(ACTION_VOICE_COMMAND).apply {
            putExtra(EXTRA_COMMAND, command)
            message?.let { putExtra("message", it) }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null // 바인딩 사용 안 함
    }
}