package com.example.recipe_pocket

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.recipe_pocket.databinding.ActivityRecipeReadBinding
import com.google.firebase.firestore.FirebaseFirestore
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RecipeReadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeReadBinding
    private lateinit var recipeStepAdapter: RecipeStepAdapter
    private lateinit var firestore: FirebaseFirestore
    private var recipeId: String? = null
    private var totalSteps = 0

    private var isVoiceRecognitionActive = false
    private val RECORD_AUDIO_PERMISSION_CODE = 101

    companion object {
        private const val TAG = "RecipeReadActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeReadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recipeId = intent.getStringExtra("RECIPE_ID")
        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Recipe ID is null.")
            finish()
            return
        }

        binding.btnClose.setOnClickListener { finish() }
        binding.soundButton.setOnClickListener { toggleVoiceRecognition() }
        updateSoundButtonState()

        initFirebase()
        setupViewPager()

        // loadRecipeData()를 코루틴 내부에서 호출하도록 변경
        lifecycleScope.launch {
            loadRecipeData()
        }

        // LocalBroadcastManager 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
            voiceCommandReceiver,
            IntentFilter(VoiceRecognitionService.ACTION_VOICE_COMMAND)
        )
    }

    private fun initFirebase() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupViewPager() {
        recipeStepAdapter = RecipeStepAdapter(emptyList())
        binding.viewPagerRecipeSteps.adapter = recipeStepAdapter

        binding.viewPagerRecipeSteps.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateStepProgressIndicator(position + 1, totalSteps)
            }
        })
    }

    private suspend fun loadRecipeData() {
        try {
            val documentSnapshot = firestore.collection("Recipes")
                .document(recipeId!!)
                .get()
                .await()

            if (documentSnapshot.exists()) {
                val recipe = documentSnapshot.toObject(Recipe::class.java)
                if (recipe != null) {
                    displayRecipe(recipe)
                } else {
                    Toast.makeText(this, "레시피 정보 변환에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Failed to convert document to Recipe object for ID: $recipeId")
                }
            } else {
                Toast.makeText(this, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "No such document with ID: $recipeId")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "데이터 로딩 실패: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error fetching recipe details for ID: $recipeId", e)
        }
    }

    private fun displayRecipe(recipe: Recipe) {
        // 이 함수는 UI를 업데이트하므로 메인 스레드에서 실행되어야 함 (launch 블록이 보장)
        // binding.etSearchBar.setText(recipe.title ?: "레시피 단계")

        if (!recipe.steps.isNullOrEmpty()) {
            totalSteps = recipe.steps.size
            // Firestore에서 가져온 stepNumber가 순서대로 정렬되어 있지 않을 수 있으므로 정렬
            val sortedSteps = recipe.steps.sortedBy { it.stepNumber }
            recipeStepAdapter.updateSteps(sortedSteps)

            if (totalSteps > 0) {
                updateStepProgressIndicator(1, totalSteps)
                binding.layoutStepProgressIndicator.visibility = View.VISIBLE
            } else {
                binding.layoutStepProgressIndicator.visibility = View.GONE
            }
        } else {
            totalSteps = 0
            Toast.makeText(this, "레시피 단계 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Recipe steps are null or empty for ID: $recipeId")
            recipeStepAdapter.updateSteps(emptyList())
            binding.layoutStepProgressIndicator.visibility = View.GONE
        }
    }
    private fun updateStepProgressIndicator(currentStep: Int, totalSteps: Int) {
        if (totalSteps > 0) {
            binding.layoutStepProgressIndicator.visibility = View.VISIBLE
            binding.tvCurrentStepTitle.text = "${currentStep}단계"
            binding.tvStepPagerIndicator.text = "$currentStep / $totalSteps"
            val progressPercentage = (currentStep.toFloat() / totalSteps.toFloat() * 100).toInt()
            binding.pbStepProgress.progress = progressPercentage
        } else {
            binding.layoutStepProgressIndicator.visibility = View.GONE
        }
    }

    private fun getCurrentViewHolder(): RecipeStepAdapter.StepViewHolder? {
        val recyclerView = binding.viewPagerRecipeSteps.getChildAt(0) as? RecyclerView
        if (recyclerView == null) {
            Log.e(TAG, "RecyclerView not found in ViewPager2")
            return null
        }
        return recyclerView.findViewHolderForAdapterPosition(binding.viewPagerRecipeSteps.currentItem) as? RecipeStepAdapter.StepViewHolder
    }


    // --- 음성인식 서비스 관련 메서드 ---
    private fun checkAndRequestAudioPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "음성 인식 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
                // 권한 허용 후, 사용자가 이전에 버튼을 눌렀다면 서비스 시작 시도
                if (shouldStartVoiceRecognitionAfterPermission) { // 새로운 플래그 사용 가능
                    startVoiceRecognitionService()
                    shouldStartVoiceRecognitionAfterPermission = false // 플래그 리셋
                }
            } else {
                Toast.makeText(this, "음성 인식 권한이 거부되었습니다. 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                isVoiceRecognitionActive = false // 서비스 시작 불가 상태
                updateSoundButtonState()
            }
        }
    }
    private var shouldStartVoiceRecognitionAfterPermission = false


    private fun toggleVoiceRecognition() {
        if (!isVoiceRecognitionActive) {
            if (checkAndRequestAudioPermission()) {
                startVoiceRecognitionService()
            } else {
                shouldStartVoiceRecognitionAfterPermission = true // 권한 승인 후 시작하도록 플래그 설정
            }
        } else {
            stopVoiceRecognitionService()
        }
    }

    private fun startVoiceRecognitionService() {
        Log.d(TAG, "Attempting to start VoiceRecognitionService.")
        isVoiceRecognitionActive = true
        updateSoundButtonState()

        val serviceIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            action = VoiceRecognitionService.ACTION_START_RECOGNITION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        }
        startService(serviceIntent)
    }

    private fun stopVoiceRecognitionService() {
        Log.d(TAG, "Attempting to stop VoiceRecognitionService.")
        isVoiceRecognitionActive = false
        updateSoundButtonState()
        val serviceIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            action = VoiceRecognitionService.ACTION_STOP_RECOGNITION
        }
        stopService(serviceIntent)
    }

    private fun updateSoundButtonState() {
        if (isVoiceRecognitionActive) {
            binding.soundButton.text = "음성인식 중지"
        } else {
            binding.soundButton.text = "음성인식 시작"
        }
    }

    private val voiceCommandReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VoiceRecognitionService.ACTION_VOICE_COMMAND) {
                val command = intent.getStringExtra(VoiceRecognitionService.EXTRA_COMMAND)
                Log.d(TAG, "Received command from service: $command")
                when (command) {
                    VoiceRecognitionService.COMMAND_NEXT -> {
                        Toast.makeText(this@RecipeReadActivity, "음성 명령: 다음", Toast.LENGTH_SHORT).show()
                        val currentItem = binding.viewPagerRecipeSteps.currentItem
                        if (currentItem < recipeStepAdapter.itemCount - 1) {
                            binding.viewPagerRecipeSteps.setCurrentItem(currentItem + 1, true)
                        } else {
                            Toast.makeText(this@RecipeReadActivity, "마지막 단계입니다. 음성인식을 종료합니다.", Toast.LENGTH_SHORT).show()
                            if (isVoiceRecognitionActive) {
                                isVoiceRecognitionActive = false
                                updateSoundButtonState()
                            }
                        }
                    }
                    VoiceRecognitionService.COMMAND_PREVIOUS -> {
                        Toast.makeText(this@RecipeReadActivity, "음성 명령: 이전", Toast.LENGTH_SHORT).show()
                        val currentItem = binding.viewPagerRecipeSteps.currentItem
                        if (currentItem > 0) {
                            binding.viewPagerRecipeSteps.setCurrentItem(currentItem - 1, true)
                        } else {
                            Toast.makeText(this@RecipeReadActivity, "첫 단계입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    VoiceRecognitionService.COMMAND_STOP_RECOGNITION_FROM_VOICE -> {
                        Toast.makeText(this@RecipeReadActivity, "음성 인식 종료", Toast.LENGTH_SHORT).show()
                        if (isVoiceRecognitionActive) {
                            isVoiceRecognitionActive = false
                            updateSoundButtonState()
                        }
                    }
                    VoiceRecognitionService.COMMAND_SERVICE_STOPPED_UNEXPECTEDLY -> {
                        val message = intent.getStringExtra("message") ?: "알 수 없는 이유"
                        Toast.makeText(this@RecipeReadActivity, "음성 서비스 예기치 않게 중지: $message", Toast.LENGTH_LONG).show()
                        if (isVoiceRecognitionActive) {
                            isVoiceRecognitionActive = false
                            updateSoundButtonState()
                        }
                    }
                    // 타이머 제어 커맨드 처리 추가
                    VoiceRecognitionService.COMMAND_TIMER_START -> {
                        Toast.makeText(this@RecipeReadActivity, "음성 명령: 타이머 시작", Toast.LENGTH_SHORT).show()
                        getCurrentViewHolder()?.startTimer()
                    }
                    VoiceRecognitionService.COMMAND_TIMER_PAUSE -> {
                        Toast.makeText(this@RecipeReadActivity, "음성 명령: 타이머 정지", Toast.LENGTH_SHORT).show()
                        getCurrentViewHolder()?.pauseTimer()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recipeStepAdapter.releaseAllTimers()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceCommandReceiver)
        if (isVoiceRecognitionActive) { // 또는 isFinishing()과 함께 체크
            Log.d(TAG, "onDestroy - Stopping VoiceRecognitionService as Activity is being destroyed.")
            stopVoiceRecognitionService()
        }
    }
}