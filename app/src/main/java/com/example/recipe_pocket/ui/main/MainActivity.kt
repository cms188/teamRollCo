package com.example.recipe_pocket.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.CategoryPageActivity
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.data.Quiz
import com.example.recipe_pocket.databinding.ActivityMainBinding
import com.example.recipe_pocket.repository.ContentLoader
import com.example.recipe_pocket.repository.CookingTipLoader
import com.example.recipe_pocket.repository.RecipeLoader
import com.example.recipe_pocket.ui.auth.LoginActivity
import com.example.recipe_pocket.ui.notification.NotificationActivity
import com.example.recipe_pocket.ui.recipe.search.SearchResult
import com.example.recipe_pocket.ui.tip.CookTipDetailActivity
import com.example.recipe_pocket.ui.tip.CookTipListActivity
import com.example.recipe_pocket.ui.user.UserPageActivity
import com.example.recipe_pocket.ui.user.bookmark.BookmarkActivity
import com.example.recipe_pocket.weather.WeatherMainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var hotCookRecipeAdapter: RecipeAdapter
    private lateinit var pickCookRecipeAdapter: RecipeAdapter
    private lateinit var simpleRecipeAdapter: RecipeAdapter
    private lateinit var cookTipAdapter: CookTipAdapter
    private lateinit var seasonalIngredientAdapter: SeasonalIngredientAdapter
    private var currentQuiz: Quiz? = null

    private var notificationListener: ListenerRegistration? = null
    private var newNotificationCount = 0 // 새로운 알림 개수를 저장할 변수

    // 알림 권한 요청을 위한 ActivityResultLauncher 선언
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 권한이 허용되었을 때의 동작 (예: 토스트 메시지)
                Toast.makeText(this, "알림 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                // 권한이 거부되었을 때의 동작
                Toast.makeText(this, "알림 권한이 거부되어 푸시 알림을 받을 수 없습니다.", Toast.LENGTH_LONG).show()
            }
        }

    // NotificationActivity로부터 결과를 받기 위한 ActivityResultLauncher
    private val notificationResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // NotificationActivity가 종료되고 돌아왔을 때 항상 리스너를 재설정하여 상태를 강제 갱신
        Log.d("NotificationDebug", "MainActivity: NotificationActivity로부터 결과 받음 (ResultCode: ${result.resultCode}). 리스너를 갱신합니다.")
        setupNotificationListener()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupClickListeners()
        setupRecyclerView()
        setupBottomNavigation()
        setupNotificationListener()

        // 알림 권한 요청 함수 호출
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        loadAllData()
        binding.bottomNavigationView.menu.findItem(R.id.fragment_home).isChecked = true
        setupNotificationListener()
    }

    override fun onPause() {
        super.onPause()
        notificationListener?.remove()
    }

    // 알림 권한을 요청하는 함수
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // 이미 권한이 부여됨
                Log.d("NotificationPermission", "알림 권한이 이미 부여되어 있습니다.")
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // 사용자가 이전에 권한을 거부한 경우, 왜 권한이 필요한지 설명 (선택 사항)
                // 예: 다이얼로그를 띄워 설명 후 다시 요청
                // 여기서는 바로 다시 요청합니다.
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // 처음으로 권한을 요청
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainFrameLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupClickListeners() {
        binding.topSearchButton.setOnClickListener {
            startActivity(Intent(this, SearchResult::class.java))
        }
        binding.categoryButton1.setOnClickListener {
            startActivity(CategoryPageActivity.createIntent(this, "한식"))
        }
        binding.categoryButton2.setOnClickListener {
            startActivity(CategoryPageActivity.createIntent(this, "양식"))
        }
        binding.categoryButton3.setOnClickListener {
            startActivity(CategoryPageActivity.createIntent(this, "중식"))
        }
        binding.categoryButton4.setOnClickListener {
            startActivity(CategoryPageActivity.createIntent(this, "일식"))
        }

        binding.topNotificationButton.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            // 인텐트에 현재 새로운 알림 개수 정보를 추가하여 전달
            intent.putExtra("new_notification_count", newNotificationCount)
            notificationResultLauncher.launch(intent)
        }
        binding.ivSeeAllTips.setOnClickListener {
            startActivity(Intent(this, CookTipListActivity::class.java))
        }
    }

    private fun setupNotificationListener() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        notificationListener?.remove()
        Log.d("NotificationDebug", "MainActivity: 알림 리스너 설정 시작.")

        val query = Firebase.firestore.collection("Notifications")
            .whereEqualTo("userId", currentUser.uid)
            .whereEqualTo("isRead", false)

        notificationListener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("NotificationDebug", "MainActivity: 알림 리스너 오류.", e)
                return@addSnapshotListener
            }

            if (snapshots != null && !snapshots.isEmpty) {
                newNotificationCount = snapshots.size() // 새로운 알림 개수 업데이트
                binding.notificationDot.visibility = View.VISIBLE
                Log.d("NotificationDebug", "MainActivity: 새로운 알림 ${newNotificationCount}개 발견. 빨간 점 표시.")
            } else {
                newNotificationCount = 0
                binding.notificationDot.visibility = View.GONE
                Log.d("NotificationDebug", "MainActivity: 새로운 알림 없음. 빨간 점 숨김.")
            }
        }
    }

    private fun loadAllData() {
        lifecycleScope.launch {
            // Hot Cook 로딩 (좋아요 순)
            RecipeLoader.loadPopularRecipes(count = 5).fold(
                onSuccess = { recipes ->
                    hotCookRecipeAdapter.updateRecipes(recipes)
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "인기 레시피 로드 실패", Toast.LENGTH_SHORT).show()
                }
            )

            // Pick Cook 로딩
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
                onSuccess = { recipes ->
                    pickCookRecipeAdapter.updateRecipes(recipes)
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "추천 레시피 로드 실패", Toast.LENGTH_SHORT).show()
                }
            )

            // 15분 이하 간단요리 로딩
            RecipeLoader.loadRecipesByCookingTime(15, 5).fold(
                onSuccess = { recipes ->
                    simpleRecipeAdapter.updateRecipes(recipes)
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "간단요리 로드 실패", Toast.LENGTH_SHORT).show()
                }
            )

            // 요리 팁 로딩
            CookingTipLoader.loadRandomTips(5).fold(
                onSuccess = { tips -> cookTipAdapter.updateData(tips) },
                onFailure = { Toast.makeText(this@MainActivity, "요리 팁 로드 실패", Toast.LENGTH_SHORT).show() }
            )

            // O/X 퀴즈 로딩
            loadNewQuiz()

            // 제철 재료 로딩
            ContentLoader.loadSeasonalIngredients().fold(
                onSuccess = { ingredients -> seasonalIngredientAdapter.updateIngredients(ingredients) },
                onFailure = { Toast.makeText(this@MainActivity, "제철 재료 로드 실패", Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun setupRecyclerView() {
        hotCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_01)
        binding.hotCookRecyclerview.apply {
            adapter = hotCookRecipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        pickCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_02)
        binding.pickCookRecyclerview.apply {
            adapter = pickCookRecipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        simpleRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_02)
        binding.simpleRecipeRecyclerview.apply {
            adapter = simpleRecipeAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        // ViewPager2 어댑터 설정 (클릭 리스너 구현)
        cookTipAdapter = CookTipAdapter(emptyList()) { tip ->
            val intent = Intent(this, CookTipDetailActivity::class.java).apply {
                putExtra(CookTipDetailActivity.EXTRA_TIP_ID, tip.id)
            }
            startActivity(intent)
        }
        binding.cookTipsViewPager.adapter = cookTipAdapter
        binding.cookTipsViewPager.offscreenPageLimit = 1

        seasonalIngredientAdapter = SeasonalIngredientAdapter(emptyList())
        binding.seasonalRecyclerview.apply {
            adapter = seasonalIngredientAdapter
            layoutManager = LinearLayoutManager(this@MainActivity, RecyclerView.HORIZONTAL, false)
        }

        // 퀴즈 카드 설정
        setupQuizCard()
    }

    private fun setupQuizCard() {
        val quizCard = binding.quizCard
        quizCard.btnQuizO.setOnClickListener { handleQuizAnswer(true) }
        quizCard.btnQuizX.setOnClickListener { handleQuizAnswer(false) }
        quizCard.btnQuizRefresh.setOnClickListener { loadNewQuiz() }
    }

    private fun loadNewQuiz() {
        lifecycleScope.launch {
            ContentLoader.loadRandomQuiz().fold(
                onSuccess = { quiz ->
                    currentQuiz = quiz
                    updateQuizUI()
                },
                onFailure = {
                    binding.quizCard.tvQuizQuestion.text = "퀴즈를 불러오는 데 실패했습니다."
                }
            )
        }
    }

    private fun updateQuizUI() {
        val quizCard = binding.quizCard
        currentQuiz?.let {
            quizCard.tvQuizQuestion.text = it.question
            quizCard.btnQuizO.isEnabled = true
            quizCard.btnQuizO.alpha = 1.0f
            quizCard.btnQuizX.isEnabled = true
            quizCard.btnQuizX.alpha = 1.0f
            quizCard.tvQuizFeedback.visibility = View.GONE
            quizCard.btnQuizRefresh.visibility = View.GONE
        }
    }

    private fun handleQuizAnswer(userAnswer: Boolean) {
        val quiz = currentQuiz ?: return
        val quizCard = binding.quizCard

        quizCard.btnQuizO.isEnabled = false
        quizCard.btnQuizX.isEnabled = false
        quizCard.btnQuizRefresh.visibility = View.VISIBLE
        quizCard.tvQuizFeedback.visibility = View.VISIBLE

        if (userAnswer == quiz.answer) {
            quizCard.tvQuizFeedback.text = "정답입니다!"
            quizCard.tvQuizFeedback.setTextColor(ContextCompat.getColor(this, R.color.success))
            if(userAnswer) quizCard.btnQuizO.alpha = 1.0f else quizCard.btnQuizX.alpha = 1.0f
        } else {
            quizCard.tvQuizFeedback.text = "오답! ${quiz.explanation}"
            quizCard.tvQuizFeedback.setTextColor(ContextCompat.getColor(this, R.color.error))
            if(userAnswer) quizCard.btnQuizO.alpha = 0.5f else quizCard.btnQuizX.alpha = 0.5f
        }
    }


    private fun setupBottomNavigation() {
        binding.bottomNavigationView.setOnItemReselectedListener { /* 아무것도 하지 않음 */ }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.fragment_home) {
                return@setOnItemSelectedListener true
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (item.itemId == R.id.fragment_favorite) {
                if (currentUser != null) {
                    WriteChoiceDialogFragment().show(supportFragmentManager, WriteChoiceDialogFragment.TAG)
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                return@setOnItemSelectedListener false
            }

            val intent = when (item.itemId) {
                R.id.fragment_search -> Intent(this, WeatherMainActivity::class.java)
                R.id.fragment_another -> Intent(this, BookmarkActivity::class.java)
                R.id.fragment_settings -> {
                    if (currentUser != null) Intent(this, UserPageActivity::class.java)
                    else Intent(this, LoginActivity::class.java)
                }
                else -> null
            }

            intent?.let {
                if (item.itemId == R.id.fragment_favorite || item.itemId == R.id.fragment_settings) {
                    startActivity(it)
                } else {
                    it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(it)
                    overridePendingTransition(0, 0)
                }
            }
            true
        }
    }
}