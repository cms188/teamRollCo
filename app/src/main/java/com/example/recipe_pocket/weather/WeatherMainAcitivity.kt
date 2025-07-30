package com.example.recipe_pocket.weather

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.data.Recipe
import com.example.recipe_pocket.databinding.WeatherBinding // weather.xml에 대한 바인딩
import com.example.recipe_pocket.repository.RecipeLoader
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WeatherMainActivity : AppCompatActivity() {

    // 뷰 바인딩 객체
    private lateinit var binding: WeatherBinding
    private lateinit var recipeAdapter: RecipeAdapter
    private val firestore = FirebaseFirestore.getInstance()

    // Activity 결과를 처리하기 위한 런처들
    private val locationResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::handleLocationResult
    )

    private val airQualityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::handleAirQualityResult
    )

    private val weatherResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::handleWeatherResult
    )

    // 날씨/미세먼지 정보를 임시 저장할 변수
    private var currentWeatherData: WeatherData? = null
    private var currentAirQualityData: AirQualityData? = null
    private var regionName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        var imageView = binding.imageview
        Glide.with(this).load(R.raw.testgif).into(imageView)

        initializeUI()
        startLocationActivity()
    }

    private fun initializeUI() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recommendationRecyclerView.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@WeatherMainActivity)
        }
    }

    private fun startLocationActivity() {
        val intent = Intent(this, LocationActivity::class.java)
        locationResultLauncher.launch(intent)
    }

    private fun handleLocationResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            regionName = result.data?.getStringExtra("REGION_NAME") ?: "알 수 없음"
            val nx = result.data?.getIntExtra("NX", 0) ?: 0
            val ny = result.data?.getIntExtra("NY", 0) ?: 0

            // 미세먼지 및 날씨 정보 요청
            airQualityResultLauncher.launch(Intent(this, AirQualityActivity::class.java).putExtra("REGION_NAME", regionName))
            weatherResultLauncher.launch(Intent(this, WeatherActivity::class.java).apply {
                putExtra("NX", nx)
                putExtra("NY", ny)
            })
        } else {
            binding.locationTextView.text = "위치 정보를 가져오는 데 실패했습니다."
            binding.weatherTextView.text = ""
        }
    }

    private fun handleAirQualityResult(result: androidx.activity.result.ActivityResult) {
        regionName = result.data?.getStringExtra("REGION_NAME") ?: regionName

        if (result.resultCode == Activity.RESULT_OK) {
            currentAirQualityData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra("AIR_QUALITY_DATA", AirQualityData::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra("AIR_QUALITY_DATA")
            }

            if (currentAirQualityData != null) {
                binding.locationTextView.text = "시간: ${currentAirQualityData!!.time} '${regionName}' 미세먼지(PM10): ${currentAirQualityData!!.pm10}µg/m³, 초미세먼지(PM2.5): ${currentAirQualityData!!.pm25}µg/m³"
                attemptToRecommendRecipes()
            } else {
                binding.locationTextView.text = "'${regionName}' 지역의 대기 질 정보를 가져올 수 없습니다."
            }
        } else {
            binding.locationTextView.text = "'${regionName}' 지역의 대기 질 정보를 가져오는 데 실패했습니다."
        }
    }

    private fun handleWeatherResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            currentWeatherData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra("WEATHER_DATA", WeatherData::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra("WEATHER_DATA")
            }

            if (currentWeatherData != null) {
                val sky = convertSkyCodeToText(currentWeatherData!!.sky)
                val pty = convertPtyCodeToText(currentWeatherData!!.pty)
                binding.weatherTextView.text = "온도: ${currentWeatherData!!.tmp}°C 발표: ${currentWeatherData!!.baseTime}\n하늘: $sky, 강수: $pty, 습도: ${currentWeatherData!!.reh}%"
                attemptToRecommendRecipes()
            } else {
                binding.weatherTextView.text = "날씨 정보를 가져올 수 없습니다."
            }
        } else {
            binding.weatherTextView.text = "날씨 정보를 가져오는 데 실패했습니다."
        }
    }

    private fun attemptToRecommendRecipes() {
        if (currentWeatherData != null && currentAirQualityData != null) {
            lifecycleScope.launch {
                val weatherTags = determineWeatherTags()
                recommendRecipes(weatherTags)
            }
        }
    }

    /**
     * 날씨 데이터를 기반으로 검색할 태그 목록을 생성합니다. (단일 책임 원칙)
     * 이 함수의 이름은 그것의 존재 이유, 기능, 사용법을 명확히 드러냅니다. (의도를 드러내는 이름)
     */
    private fun determineWeatherTags(): Map<String, List<String>> {
        val weatherData = currentWeatherData ?: return emptyMap()
        val airData = currentAirQualityData ?: return emptyMap()

        val tags = mutableMapOf<String, MutableList<String>>()

        // 1. 날씨 현상 태그
        tags["weather"] = mutableListOf()
        val ptyCode = weatherData.pty.toIntOrNull() ?: 0
        if (ptyCode in listOf(1, 2, 5, 6)) {
            tags["weather"]?.add("비오는날")
        } else if (ptyCode in listOf(3, 7)) {
            tags["weather"]?.add("눈오는날")
        } else { // pty가 0 (강수 없음)일 경우 sky 값 사용
            val skyCode = weatherData.sky.toIntOrNull() ?: 0
            when (skyCode) {
                1 -> tags["weather"]?.add("맑은날")
                3, 4 -> tags["weather"]?.add("흐린날")
            }
        }

        // 2. 온도 태그
        tags["temperature"] = mutableListOf()
        val temp = weatherData.tmp.toFloatOrNull() ?: return tags
        when {
            temp >= 33 -> tags["temperature"]?.add("매우더운날")
            temp in 27.0..32.9 -> tags["temperature"]?.add("더운날")
            temp in 12.0..26.9 -> tags["temperature"]?.add("쾌적한날")
            temp in 0.0..11.9 -> tags["temperature"]?.add("추운날")
            else -> tags["temperature"]?.add("매우추운날")
        }

        // 3. 미세먼지 태그
        tags["dust"] = mutableListOf()
        val pm10 = airData.pm10.toIntOrNull() ?: return tags
        when {
            pm10 in 0..30 -> tags["dust"]?.add("미세먼지좋음")
            pm10 in 31..80 -> tags["dust"]?.add("미세먼지보통")
            pm10 in 81..150 -> tags["dust"]?.add("미세먼지나쁨")
            else -> tags["dust"]?.add("미세먼지매우나쁨")
        }

        // 4. 습도 태그
        tags["humidity"] = mutableListOf()
        val humidity = weatherData.reh.toIntOrNull() ?: return tags
        when {
            humidity >= 60 -> tags["humidity"]?.add("습도높음")
            humidity <= 40 -> tags["humidity"]?.add("습도낮음")
        }

        return tags
    }

    /**
     * 우선순위에 따라 태그를 조합하여 레시피를 검색하고 UI에 표시합니다. (단계별 규칙)
     */
    private suspend fun recommendRecipes(tags: Map<String, List<String>>) {
        binding.recommendationTitleTextView.visibility = View.VISIBLE
        binding.recommendationProgressBar.visibility = View.VISIBLE
        binding.recommendationStatusTextView.visibility = View.GONE
        binding.recommendationRecyclerView.visibility = View.GONE

        val priorities = listOf("weather", "temperature", "dust", "humidity")
        var finalRecipes = listOf<Recipe>()

        // 4개 태그부터 1개 태그까지 우선순위대로 검색
        for (i in priorities.size downTo 1) {
            val currentPriorityTags = priorities.take(i)
            val searchTags = currentPriorityTags.flatMap { key -> tags[key] ?: emptyList() }

            if (searchTags.isEmpty()) continue

            val result = queryRecipesWithTags(searchTags)
            if (result.isNotEmpty()) {
                finalRecipes = result
                break // 결과를 찾으면 루프 중단
            }
        }

        binding.recommendationProgressBar.visibility = View.GONE
        if (finalRecipes.isNotEmpty()) {
            binding.recommendationRecyclerView.visibility = View.VISIBLE
            recipeAdapter.updateRecipes(finalRecipes)
        } else {
            binding.recommendationStatusTextView.text = "오늘 날씨에 맞는 추천 레시피를 찾지 못했어요."
            binding.recommendationStatusTextView.visibility = View.VISIBLE
        }
    }

    /**
     * 주어진 태그 목록을 사용하여 Firestore에서 레시피를 쿼리합니다. (단일 책임 원칙)
     */
    private suspend fun queryRecipesWithTags(tags: List<String>): List<Recipe> {
        if (tags.isEmpty()) return emptyList()

        return try {
            // Firestore는 'whereIn'과 'whereArrayContains'를 동시에 사용할 수 없으므로,
            // 'whereArrayContainsAny'를 사용하여 여러 태그 중 하나라도 포함된 문서를 찾습니다.
            // Firestore는 최대 30개의 값을 'whereArrayContainsAny'에 사용할 수 있습니다.
            val querySnapshot = firestore.collection("Recipes")
                .whereArrayContainsAny("tags", tags.take(30))
                .get()
                .await()

            val recipesWithAuthor = coroutineScope {
                querySnapshot.documents
                    .map { async { RecipeLoader.enrichRecipeWithAuthor(it) } }
                    .awaitAll()
                    .filterNotNull()
            }

            // 클라이언트 측에서 모든 태그가 포함되어 있는지 필터링합니다.
            // 또한 중복을 제거합니다.
            recipesWithAuthor
                .filter { recipe -> recipe.tags?.containsAll(tags) == true }
                .distinctBy { it.id }

        } catch (e: Exception) {
            // 오류 처리: 로깅하고 빈 리스트 반환
            println("Firestore 쿼리 오류: ${e.message}")
            emptyList<Recipe>()
        }
    }

    private fun convertSkyCodeToText(sky: String) = when(sky) {
        "1" -> "맑음"
        "3" -> "구름많음"
        "4" -> "흐림"
        else -> "정보 없음($sky)"
    }

    private fun convertPtyCodeToText(pty: String) = when(pty) {
        "0" -> "강수 없음"
        "1" -> "비"
        "2" -> "비/눈"
        "3" -> "눈"
        "5" -> "빗방울"
        "6" -> "빗방울/눈날림"
        "7" -> "눈날림"
        else -> "정보 없음($pty)"
    }
}