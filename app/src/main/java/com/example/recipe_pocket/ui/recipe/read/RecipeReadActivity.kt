package com.example.recipe_pocket.ui.recipe.read

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
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.recipe_pocket.service.VoiceRecognitionService
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.ActivityRecipeReadBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RecipeReadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeReadBinding
    private lateinit var recipeStepAdapter: RecipeStepAdapter
    private lateinit var firestore: FirebaseFirestore
    // ▼▼▼ 이 줄을 추가해주세요 ▼▼▼
    private lateinit var auth: FirebaseAuth
    private var recipeId: String? = null
    private var totalSteps = 0

    private var isVoiceRecognitionActive = false
    private var shouldStartVoiceRecognitionAfterPermission = false
    private val PERMISSION_REQUEST_CODE = 101

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
            finish()
            return
        }

        binding.btnClose.setOnClickListener { finish() }
        binding.soundButton.setOnClickListener { toggleVoiceRecognition() }
        updateSoundButtonState()

        initFirebase()
        setupViewPager()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            voiceCommandReceiver,
            IntentFilter(VoiceRecognitionService.Companion.ACTION_VOICE_COMMAND)
        )
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            loadRecipeData()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recipeStepAdapter.releaseAllTimers()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceCommandReceiver)
        if (isVoiceRecognitionActive) {
            stopVoiceRecognitionService()
        }
    }

    private fun initFirebase() {
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
    }

    private fun setToolbarVisibility(isVisible: Boolean) {
        binding.topBarsContainer.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun setupViewPager() {
        recipeStepAdapter = RecipeStepAdapter(null)
        binding.viewPagerRecipeSteps.adapter = recipeStepAdapter

        binding.viewPagerRecipeSteps.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val isStepPage = position < totalSteps
                setToolbarVisibility(isStepPage)

                if (isStepPage) {
                    updateStepProgressIndicator(position + 1, totalSteps)
                }
            }
        })
    }

    private suspend fun loadRecipeData() {
        try {
            val documentSnapshot = firestore.collection("Recipes").document(recipeId!!).get().await()
            if (documentSnapshot.exists()) {
                val recipe = documentSnapshot.toObject(Recipe::class.java)
                if (recipe != null) {
                    val currentUserId = auth.currentUser?.uid // 수정된 부분
                    if (currentUserId != null) {
                        recipe.isBookmarked = recipe.bookmarkedBy?.contains(currentUserId) == true
                    }
                    displayRecipe(recipe)
                } else {
                    Toast.makeText(this, "레시피 변환 실패", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "데이터 로딩 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayRecipe(recipe: Recipe) {
        totalSteps = recipe.steps?.size ?: 0
        recipeStepAdapter.updateItems(recipe)

        if (totalSteps > 0) {
            setToolbarVisibility(true)
            updateStepProgressIndicator(1, totalSteps)
        } else {
            setToolbarVisibility(false)
        }
    }

    private fun updateStepProgressIndicator(currentStep: Int, totalSteps: Int) {
        binding.tvCurrentStepTitle.text = "${currentStep}단계"
        binding.tvStepPagerIndicator.text = "$currentStep / $totalSteps"
        binding.pbStepProgress.progress = (currentStep.toFloat() / totalSteps.toFloat() * 100).toInt()
    }

    private fun getCurrentViewHolder(): RecipeStepAdapter.StepViewHolder? {
        val recyclerView = binding.viewPagerRecipeSteps.getChildAt(0) as? RecyclerView ?: return null
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(binding.viewPagerRecipeSteps.currentItem)
        return viewHolder as? RecipeStepAdapter.StepViewHolder
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
                if (shouldStartVoiceRecognitionAfterPermission) {
                    startVoiceRecognitionService()
                    shouldStartVoiceRecognitionAfterPermission = false
                }
            } else {
                Toast.makeText(this, "권한이 거부되어 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                isVoiceRecognitionActive = false
                updateSoundButtonState()
            }
        }
    }

    private fun toggleVoiceRecognition() {
        if (!isVoiceRecognitionActive) {
            if (checkAndRequestPermissions()) {
                startVoiceRecognitionService()
            } else {
                shouldStartVoiceRecognitionAfterPermission = true
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
            action = VoiceRecognitionService.Companion.ACTION_START_RECOGNITION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopVoiceRecognitionService() {
        Log.d(TAG, "Attempting to stop VoiceRecognitionService.")
        isVoiceRecognitionActive = false
        updateSoundButtonState()
        val serviceIntent = Intent(this, VoiceRecognitionService::class.java).apply {
            action = VoiceRecognitionService.Companion.ACTION_STOP_RECOGNITION
        }
        stopService(serviceIntent)
    }

    private fun updateSoundButtonState() {
        binding.soundButton.text = if (isVoiceRecognitionActive) "음성인식 중지" else "음성인식 시작"
    }

    private val voiceCommandReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VoiceRecognitionService.Companion.ACTION_VOICE_COMMAND) {
                val command = intent.getStringExtra(VoiceRecognitionService.Companion.EXTRA_COMMAND)
                Log.d(TAG, "Received command from service: $command")
                when (command) {
                    VoiceRecognitionService.Companion.COMMAND_NEXT -> {
                        val currentItem = binding.viewPagerRecipeSteps.currentItem
                        if (currentItem < recipeStepAdapter.itemCount - 1) {
                            binding.viewPagerRecipeSteps.setCurrentItem(currentItem + 1, true)
                        } else {
                            Toast.makeText(this@RecipeReadActivity, "마지막입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    VoiceRecognitionService.Companion.COMMAND_PREVIOUS -> {
                        val currentItem = binding.viewPagerRecipeSteps.currentItem
                        if (currentItem > 0) {
                            binding.viewPagerRecipeSteps.setCurrentItem(currentItem - 1, true)
                        } else {
                            Toast.makeText(this@RecipeReadActivity, "첫 단계입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    VoiceRecognitionService.Companion.COMMAND_STOP_RECOGNITION_FROM_VOICE -> {
                        stopVoiceRecognitionService()
                    }
                    VoiceRecognitionService.Companion.COMMAND_SERVICE_STOPPED_UNEXPECTEDLY -> {
                        val message = intent.getStringExtra("message") ?: "알 수 없는 이유"
                        Toast.makeText(this@RecipeReadActivity, "음성 서비스 중지: $message", Toast.LENGTH_LONG).show()
                        if (isVoiceRecognitionActive) {
                            isVoiceRecognitionActive = false
                            updateSoundButtonState()
                        }
                    }
                    VoiceRecognitionService.Companion.COMMAND_TIMER_START -> getCurrentViewHolder()?.startTimer()
                    VoiceRecognitionService.Companion.COMMAND_TIMER_PAUSE -> getCurrentViewHolder()?.pauseTimer()
                }
            }
        }
    }
}