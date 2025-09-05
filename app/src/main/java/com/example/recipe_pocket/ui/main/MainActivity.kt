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
import com.example.recipe_pocket.databinding.ActivityMainBinding
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
    private lateinit var nCookRecipeAdapter: RecipeAdapter
    private lateinit var cookTipAdapter: CookTipAdapter

    private var notificationListener: ListenerRegistration? = null
    private var newNotificationCount = 0 // 새로운 알림 개수를 저장할 변수

    // ★★★ 알림 권한 요청을 위한 ActivityResultLauncher 선언 ★★★
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
        // NotificationActivity가 종료되고 돌아왔을 때 항상 리스너를 재설정하여 상태를 강제 갱신합니다.
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

        // ★★★ 알림 권한 요청 함수 호출 ★★★
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

    // ★★★ 알림 권한을 요청하는 함수 추가 ★★★
    private fun askNotificationPermission() {
        // 이 코드는 Android 13 (API 레벨 33) 이상에서만 실행됩니다.
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
            startActivity(Intent(this, CategoryPageActivity::class.java))
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
            // Hot Cook 로딩
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
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

            // N Cook 로딩
            RecipeLoader.loadMultipleRandomRecipesWithAuthor(count = 5).fold(
                onSuccess = { recipes ->
                    nCookRecipeAdapter.updateRecipes(recipes)
                },
                onFailure = {
                    Toast.makeText(this@MainActivity, "신규 레시피 로드 실패", Toast.LENGTH_SHORT).show()
                }
            )

            // 요리 팁 로딩 추가
            CookingTipLoader.loadRandomTips(5).fold(
                onSuccess = { tips -> cookTipAdapter.updateData(tips) },
                onFailure = { Toast.makeText(this@MainActivity, "요리 팁 로드 실패", Toast.LENGTH_SHORT).show() }
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

        nCookRecipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_02)
        binding.nCookRecyclerview.apply {
            adapter = nCookRecipeAdapter
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
                    // 로그인 상태: 작성 선택 다이얼로그 표시
                    WriteChoiceDialogFragment().show(supportFragmentManager, WriteChoiceDialogFragment.TAG)
                } else {
                    // 비로그인 상태: 로그인 화면으로 이동
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                // 이벤트 처리를 여기서 완료했음을 알림
                return@setOnItemSelectedListener false // reselection 방지를 위해 false 반환
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