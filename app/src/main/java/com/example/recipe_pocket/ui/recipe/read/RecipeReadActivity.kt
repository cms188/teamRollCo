package com.example.recipe_pocket.ui.recipe.read

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.recipe_pocket.R
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.service.FloatingTimerService
import com.example.recipe_pocket.service.VoiceRecognitionService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RecipeReadActivity : AppCompatActivity() {

    private lateinit var recipeStepAdapter: RecipeStepAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var recipeId: String? = null
    private var currentRecipe: Recipe? = null // 레시피 데이터 저장
    private var totalSteps = 0

    // UI 요소
    private lateinit var viewPager: ViewPager2
    private lateinit var backButton: ImageButton
    private lateinit var allStepsButton: LinearLayout
    private lateinit var voiceButton: LinearLayout
    private lateinit var stepProgressIndicatorLayout: CardView // 타입을 CardView로 수정
    private lateinit var stepBadgeTextView: TextView
    private lateinit var currentStepTitleTextView: TextView
    private lateinit var stepPagerIndicatorTextView: TextView
    private lateinit var stepProgressBar: ProgressBar


    private var isVoiceRecognitionActive = false
    private var shouldStartVoiceRecognitionAfterPermission = false
    private val PERMISSION_REQUEST_CODE = 101

    // 플로팅 타이머 관련 변수
    private var activeTimerViewHolder: RecipeStepAdapter.StepViewHolder? = null
    private var isTimerRunningForFloating = false

    companion object {
        private const val TAG = "RecipeReadActivity"
        const val EXTRA_TARGET_STEP = "TARGET_STEP"
        const val EXTRA_REMAINING_TIME = "REMAINING_TIME"
        const val EXTRA_FROM_FLOATING_TIMER = "FROM_FLOATING_TIMER"
    }

    private val permissionResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkOverlayPermission(showDialog = false)) {
            launchFloatingTimer()
        } else {
            Toast.makeText(this, "플로팅 타이머를 사용하려면 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // '모든 단계 보기' 액티비티를 위한 런처 추가
    private val allStepsResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedIndex = result.data?.getIntExtra(AllStepsActivity.RESULT_SELECTED_STEP_INDEX, -1) ?: -1
            if (selectedIndex != -1) {
                viewPager.setCurrentItem(selectedIndex, true)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_read_recent)

        initFirebase()
        initializeViews()
        setupToolbar() // 툴바 설정 추가
        setupUI()
        processIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // 앱으로 돌아왔을 때, 실행 중인 플로팅 타이머 서비스가 있다면 중지
    }

    // 뷰 ID를 이용해 초기화
    private fun initializeViews() {
        backButton = findViewById(R.id.back_button)
        viewPager = findViewById(R.id.view_pager_recipe_steps)
        stepProgressIndicatorLayout = findViewById(R.id.layout_step_progress_indicator)
        stepBadgeTextView = findViewById(R.id.tv_step_badge)
        currentStepTitleTextView = findViewById(R.id.tv_current_step_title)
        stepPagerIndicatorTextView = findViewById(R.id.tv_step_pager_indicator)
        stepProgressBar = findViewById(R.id.pb_step_progress)
        allStepsButton = findViewById(R.id.btn_previous_step)
        voiceButton = findViewById(R.id.btn_next_step)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val toolbarTitle = findViewById<TextView>(R.id.toolbar_title)
        val editDeleteContainer = findViewById<View>(R.id.edit_delete_container)

        // 제목 설정 및 불필요한 버튼 숨기기
        toolbarTitle.text = "요리하기"
        editDeleteContainer.visibility = View.GONE

        // 상태바 높이만큼 패딩 추가하여 레이아웃 밀림 방지
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                height = statusBarHeight + (56 * resources.displayMetrics.density).toInt()
            }
            WindowInsetsCompat.CONSUMED
        }
    }


    private fun processIntent(intent: Intent?) {
        if (intent == null) {
            Toast.makeText(this, "레시피 정보를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        recipeId = intent.getStringExtra("RECIPE_ID")
        if (recipeId == null) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val isFromFloatingTimer = intent.getBooleanExtra(EXTRA_FROM_FLOATING_TIMER, false)

        lifecycleScope.launch {
            loadRecipeData {
                if (isFromFloatingTimer) {
                    val targetStep = intent.getIntExtra(EXTRA_TARGET_STEP, -1)
                    val remainingTime = intent.getLongExtra(EXTRA_REMAINING_TIME, -1)
                    if (targetStep != -1 && remainingTime != -1L) {
                        restoreTimerState(targetStep, remainingTime)
                    }
                }
            }
        }
    }

    private fun setupUI() {
        backButton.setOnClickListener { finish() }
        voiceButton.setOnClickListener { toggleVoiceRecognition() }
        updateSoundButtonState() // 버튼 텍스트 대신 아이콘 상태 등으로 변경 가능
        setupViewPager()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            voiceCommandReceiver,
            IntentFilter(VoiceRecognitionService.ACTION_VOICE_COMMAND)
        )

        // '모든 단계 보기' 버튼 클릭 리스너
        allStepsButton.setOnClickListener {
            showAllStepsActivity()
        }
    }

    // 모든 단계 보기 액티비티 호출
    private fun showAllStepsActivity() {
        val steps = currentRecipe?.steps ?: return
        if (steps.isEmpty()) return

        val stepTitles = ArrayList(steps.sortedBy { it.stepNumber }.mapIndexed { index, step ->
            "${index + 1}단계: ${step.title?.takeIf { it.isNotEmpty() } ?: "요리하기"}"
        })

        val intent = Intent(this, AllStepsActivity::class.java).apply {
            putStringArrayListExtra(AllStepsActivity.EXTRA_STEP_TITLES, stepTitles)
        }
        allStepsResultLauncher.launch(intent)
    }


    override fun onDestroy() {
        super.onDestroy()
        recipeStepAdapter.releaseAllTimers()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceCommandReceiver)
        if (isVoiceRecognitionActive) {
            stopVoiceRecognitionService()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isTimerRunningForFloating) {
            // 다른 타이머가 떠있을 수 있으므로, 현재 액티비티를 나가기 전에 기존 플로팅 타이머 서비스를 중지.
            stopService(Intent(this, FloatingTimerService::class.java).apply {
                action = FloatingTimerService.ACTION_STOP
            })
            if (checkOverlayPermission(showDialog = true)) {
                launchFloatingTimer()
            }
        }
    }

    override fun onBackPressed() {
        if (isTimerRunningForFloating) {
            // 다른 타이머가 떠있을 수 있으므로, 현재 액티비티를 나가기 전에 기존 플로팅 타이머 서비스를 중지.
            stopService(Intent(this, FloatingTimerService::class.java).apply {
                action = FloatingTimerService.ACTION_STOP
            })
            if (checkOverlayPermission(showDialog = true)) {
                launchFloatingTimer()
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }

    private fun initFirebase() {
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
    }

    private fun updateTopUIForPage(isStepPage: Boolean, currentStep: Int = 0) {
        stepProgressIndicatorLayout.visibility = if (isStepPage) View.VISIBLE else View.GONE
        if (isStepPage) {
            val stepData = currentRecipe?.steps?.getOrNull(currentStep)
            stepBadgeTextView.text = "${currentStep + 1}단계"
            currentStepTitleTextView.text = stepData?.title?.takeIf { it.isNotEmpty() } ?: "요리하기"
        }
    }


    private fun setupViewPager() {
        recipeStepAdapter = RecipeStepAdapter(null, object : RecipeStepAdapter.OnTimerStateChangedListener {
            override fun onTimerStart() { isTimerRunningForFloating = true }
            override fun onTimerPause() { isTimerRunningForFloating = false }
            override fun onTimerStop() { isTimerRunningForFloating = false }
        })

        viewPager.adapter = recipeStepAdapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val isStepPage = position < totalSteps
                updateTopUIForPage(isStepPage, position)

                if (isStepPage) {
                    updateStepProgressIndicator(position + 1, totalSteps)
                    activeTimerViewHolder = getCurrentViewHolder()
                } else {
                    activeTimerViewHolder = null
                }
            }
        })
    }

    private suspend fun loadRecipeData(onComplete: (() -> Unit)? = null) {
        try {
            val documentSnapshot = firestore.collection("Recipes").document(recipeId!!).get().await()
            if (documentSnapshot.exists()) {
                val recipe = documentSnapshot.toObject(Recipe::class.java)
                if (recipe != null) {
                    currentRecipe = recipe // 레시피 데이터 저장
                    val currentUserId = auth.currentUser?.uid
                    if (currentUserId != null) {
                        recipe.isBookmarked = recipe.bookmarkedBy?.contains(currentUserId) == true
                    }
                    displayRecipe(recipe)
                    onComplete?.invoke()
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

        val isStepPage = totalSteps > 0
        updateTopUIForPage(isStepPage, 0)
        if (isStepPage) {
            updateStepProgressIndicator(1, totalSteps)
        }
    }

    private fun restoreTimerState(targetStep: Int, remainingTime: Long) {
        viewPager.post {
            viewPager.setCurrentItem(targetStep, false)
            viewPager.post {
                val vh = getCurrentViewHolder()
                vh?.setRemainingTime(remainingTime)
                vh?.startTimer()
            }
        }
    }

    private fun updateStepProgressIndicator(currentStep: Int, totalSteps: Int) {
        stepPagerIndicatorTextView.text = "$currentStep / $totalSteps"
        stepProgressBar.progress = (currentStep.toFloat() / totalSteps.toFloat() * 100).toInt()
    }

    private fun getCurrentViewHolder(): RecipeStepAdapter.StepViewHolder? {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return null
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(viewPager.currentItem)
        return viewHolder as? RecipeStepAdapter.StepViewHolder
    }

    private fun checkOverlayPermission(showDialog: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            if (showDialog) {
                AlertDialog.Builder(this)
                    .setTitle("권한 필요")
                    .setMessage("다른 앱 위에 타이머를 표시하려면 권한이 필요합니다. 설정으로 이동하시겠습니까?")
                    .setPositiveButton("설정") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        permissionResultLauncher.launch(intent)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
            return false
        }
        return true
    }

    private fun launchFloatingTimer() {
        val vh = activeTimerViewHolder
        if (vh != null && vh.isTimerRunning()) {
            val intent = Intent(this, FloatingTimerService::class.java).apply {
                action = FloatingTimerService.ACTION_START
                putExtra(FloatingTimerService.EXTRA_INITIAL_TIME, vh.getInitialTimeInMillis())
                putExtra(FloatingTimerService.EXTRA_TIME_IN_MILLIS, vh.getTimeLeftInMillis())
                putExtra(FloatingTimerService.EXTRA_RECIPE_ID, recipeId)
                putExtra(FloatingTimerService.EXTRA_STEP_POSITION, viewPager.currentItem)
            }
            startService(intent)
        }
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
            action = VoiceRecognitionService.ACTION_START_RECOGNITION
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
            action = VoiceRecognitionService.ACTION_STOP_RECOGNITION
        }
        stopService(serviceIntent)
    }

    private fun updateSoundButtonState() {
        val icon = voiceButton.findViewById<ImageView>(R.id.btn_next_step_icon)
        val text = voiceButton.findViewById<TextView>(R.id.btn_next_step_text)
        val alphaValue = if (isVoiceRecognitionActive) 1.0f else 0.5f

        icon.alpha = alphaValue
        text.alpha = alphaValue
    }


    private val voiceCommandReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VoiceRecognitionService.ACTION_VOICE_COMMAND) {
                val command = intent.getStringExtra(VoiceRecognitionService.EXTRA_COMMAND)
                Log.d(TAG, "Received command from service: $command")
                when (command) {
                    VoiceRecognitionService.COMMAND_NEXT -> {
                        val currentItem = viewPager.currentItem
                        if (currentItem < recipeStepAdapter.itemCount - 1) {
                            viewPager.setCurrentItem(currentItem + 1, true)
                        } else {
                            Toast.makeText(this@RecipeReadActivity, "마지막입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    VoiceRecognitionService.COMMAND_PREVIOUS -> {
                        val currentItem = viewPager.currentItem
                        if (currentItem > 0) {
                            viewPager.setCurrentItem(currentItem - 1, true)
                        } else {
                            Toast.makeText(this@RecipeReadActivity, "첫 단계입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    VoiceRecognitionService.COMMAND_STOP_RECOGNITION_FROM_VOICE -> {
                        stopVoiceRecognitionService()
                    }
                    VoiceRecognitionService.COMMAND_SERVICE_STOPPED_UNEXPECTEDLY -> {
                        val message = intent.getStringExtra("message") ?: "알 수 없는 이유"
                        Toast.makeText(this@RecipeReadActivity, "음성 서비스 중지: $message", Toast.LENGTH_LONG).show()
                        if (isVoiceRecognitionActive) {
                            isVoiceRecognitionActive = false
                            updateSoundButtonState()
                        }
                    }
                    VoiceRecognitionService.COMMAND_TIMER_START -> getCurrentViewHolder()?.startTimer()
                    VoiceRecognitionService.COMMAND_TIMER_PAUSE -> getCurrentViewHolder()?.pauseTimer()
                }
            }
        }
    }
}