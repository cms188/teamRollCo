package com.example.recipe_pocket.weather

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
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
import org.shredzone.commons.suncalc.SunTimes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WeatherMainActivity : AppCompatActivity() {

    // 뷰 바인딩 객체
    private lateinit var binding: WeatherBinding
    private lateinit var recipeAdapter: RecipeAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var btnback: ImageView

    companion object {
        private const val SEOUL_LAT = 37.5665
        private const val SEOUL_LON = 126.9780
    }

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
    private var regionSidoName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupFloatingTopBar()
        initializeUI()
        startLocationActivity()

        btnback = findViewById(R.id.iv_back_button)
        btnback.setOnClickListener {
            finish()
        }
    }

    private fun initializeUI() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recommendationRecyclerView.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(this@WeatherMainActivity)
        }
    }

    private fun setupFloatingTopBar() {
        val toolbar = binding.topBarLayout
        val baseHeight = (56 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarHeight)
            view.updateLayoutParams<ViewGroup.LayoutParams> {
                height = statusBarHeight + baseHeight
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun startLocationActivity() {
        val intent = Intent(this, LocationActivity::class.java)
        locationResultLauncher.launch(intent)
    }

    private fun handleLocationResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val displayName = result.data?.getStringExtra("REGION_NAME") ?: "알 수 없음"
            val sidoName = result.data?.getStringExtra("SIDO_NAME") ?: displayName
            regionName = displayName
            regionSidoName = sidoName
            val nx = result.data?.getIntExtra("NX", 0) ?: 0
            val ny = result.data?.getIntExtra("NY", 0) ?: 0

            // 미세먼지 및 날씨 정보 요청
            val airQualityIntent = Intent(this, AirQualityActivity::class.java).apply {
                putExtra("REGION_NAME", displayName)
                putExtra("SIDO_NAME", sidoName)
            }
            airQualityResultLauncher.launch(airQualityIntent)
            weatherResultLauncher.launch(Intent(this, WeatherActivity::class.java).apply {
                putExtra("NX", nx)
                putExtra("NY", ny)
            })
        } else {
            binding.locationTextView.text = "위치 정보를 가져오는 데 실패했습니다."
            binding.tempTextView.text = ""
        }
    }

    private fun handleAirQualityResult(result: androidx.activity.result.ActivityResult) {
        regionName = result.data?.getStringExtra("REGION_NAME") ?: regionName
        regionSidoName = result.data?.getStringExtra("SIDO_NAME") ?: regionSidoName

        if (result.resultCode == Activity.RESULT_OK) {
            currentAirQualityData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra("AIR_QUALITY_DATA", AirQualityData::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra("AIR_QUALITY_DATA")
            }

            if (currentAirQualityData != null) {
                //binding.locationTextView.text = "시간: ${currentAirQualityData!!.time} '${regionName}' 미세먼지(PM10): ${currentAirQualityData!!.pm10}µg/m³, 초미세먼지(PM2.5): ${currentAirQualityData!!.pm25}µg/m³"
                updateWeatherBackground()
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
                binding.skyTextView.text = convertSkyCodeToText(currentWeatherData!!.sky)
                val pty = convertPtyCodeToText(currentWeatherData!!.pty)
                binding.tempTextView.text = "${currentWeatherData!!.tmp}°"
                binding.humidityTextView.text = "습도 ${currentWeatherData!!.reh}%"
                binding.popTextView.text = "강수확률 ${currentWeatherData!!.pop}%"
                attemptToRecommendRecipes()
            } else {
                binding.tempTextView.text = ""
            }
        } else {
            binding.tempTextView.text = ""
        }
    }

    private fun attemptToRecommendRecipes() {
        if (currentWeatherData != null && currentAirQualityData != null) {
            val temp = currentWeatherData!!.tmp
            val region = regionName ?: ""
            val humidity = currentWeatherData!!.reh
            val pop = currentWeatherData!!.pop
            var pm10 = currentAirQualityData!!.pm10.toIntOrNull()
            var pm25 = currentAirQualityData!!.pm25.toIntOrNull()

            if (pm10 != null) {
                when {
                    pm10 in 0..30 -> binding.pm10ImageView.setImageResource(R.drawable.verygood)
                    pm10 in 31..80 -> binding.pm10ImageView.setImageResource(R.drawable.good)
                    pm10 in 81..150 -> binding.pm10ImageView.setImageResource(R.drawable.bad)
                    else -> binding.pm10ImageView.setImageResource(R.drawable.verybad)
                }
            }

            if (pm25 != null) {
                when {
                    pm25 in 0..15 -> binding.pm25ImageView.setImageResource(R.drawable.verygood)
                    pm25 in 16..35 -> binding.pm25ImageView.setImageResource(R.drawable.good)
                    pm25 in 36..76 -> binding.pm25ImageView.setImageResource(R.drawable.bad)
                    else -> binding.pm25ImageView.setImageResource(R.drawable.verybad)
                }
            }
            binding.tempTextView.text = "${temp}°"
            binding.locationTextView.text = region
            binding.humidityTextView.text = "습도 ${humidity}%"
            binding.popTextView.text = "강수확률 ${pop}%"



            //binding.pm10TextView.text = "미세먼지 ${currentAirQualityData!!.pm10}µg/m³"
            //binding.pm25TextView.text = "초미세먼지 ${currentAirQualityData!!.pm25}µg/m³"

            lifecycleScope.launch {
                val weatherTags = determineWeatherTags()
                recommendRecipes(weatherTags)
            }
        }
    }

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
        val temp = weatherData.tmp.toFloatOrNull()
        if (temp != null) {
            when {
                temp >= 33 -> tags["temperature"]?.add("매우더운날")
                temp in 29.0..32.9 -> tags["temperature"]?.add("더운날")
                temp in 19.0..28.9 -> tags["temperature"]?.add("쾌적한날")
                temp in 8.0..18.9 -> tags["temperature"]?.add("추운날")
                else -> tags["temperature"]?.add("매우추운날")
            }
        }

        // 3. 미세먼지 태그
        tags["dust"] = mutableListOf()
        val pm10 = airData.pm10.toIntOrNull()
        if (pm10 != null) {
            when {
                pm10 in 0..30 -> tags["dust"]?.add("미세먼지좋음")
                pm10 in 31..80 -> tags["dust"]?.add("미세먼지보통")
                pm10 in 81..150 -> tags["dust"]?.add("미세먼지나쁨")
                else -> tags["dust"]?.add("미세먼지매우나쁨")
            }
        }

        // 4. 습도 태그
        tags["humidity"] = mutableListOf()
        val humidity = weatherData.reh.toIntOrNull()
        if (humidity != null) {
            when {
                humidity >= 60 -> tags["humidity"]?.add("습도높음")
                humidity <= 40 -> tags["humidity"]?.add("습도낮음")
            }
        }

        return tags
    }

    private suspend fun recommendRecipes(tags: Map<String, List<String>>) {
        binding.recommendationTitleTextView.visibility = View.VISIBLE
        binding.recommendationProgressBar.visibility = View.VISIBLE
        binding.recommendationStatusTextView.visibility = View.GONE
        binding.recommendationRecyclerView.visibility = View.GONE

        // 카테고리별 대표 태그 선택 (설계상 각 1개)
        val tempTag = tags["temperature"]?.firstOrNull()
        val weatherTag = tags["weather"]?.firstOrNull()
        val dustTag = tags["dust"]?.firstOrNull()
        val humidityTag = tags["humidity"]?.firstOrNull()

        // 시작 조건: 온도 태그가 없으면 날씨부터 시도, 둘 다 없으면 종료 메시지
        val requiredAll = mutableListOf<String>()
        if (tempTag != null) {
            requiredAll += tempTag
            if (weatherTag != null) requiredAll += weatherTag
        } else if (weatherTag != null) {
            requiredAll += weatherTag
        } else {
            binding.recommendationProgressBar.visibility = View.GONE
            binding.recommendationStatusTextView.text = "오늘 날씨에 맞는 추천 레시피를 찾지 못했어요."
            binding.recommendationStatusTextView.visibility = View.VISIBLE
            return
        }

        // 우선 순서대로 시도할 조합 생성
        val baseTW = requiredAll.toList() // temperature + (optional) weather
        val attempts = mutableListOf<List<String>>()

        // Attempt 1: base + dust + humidity (있는 것만)
        var full = baseTW
        if (dustTag != null) full = full + dustTag
        if (humidityTag != null) full = full + humidityTag
        if (full.isNotEmpty()) attempts.add(full)

        // Attempt 2: drop humidity (먼지 유지)
        if (humidityTag != null) {
            val noHumidity = full.filter { it != humidityTag }
            if (noHumidity.isNotEmpty() && (attempts.isEmpty() || attempts.last() != noHumidity)) {
                attempts.add(noHumidity)
            }
        }

        // Attempt 3: dust 제거 (temperature + weather)
        if (dustTag != null) {
            if (baseTW.isNotEmpty() && (attempts.isEmpty() || attempts.last() != baseTW)) {
                attempts.add(baseTW)
            }
        }

        // Attempt 4: weather 제거 (temperature만)
        if (tempTag != null && weatherTag != null) {
            val tempOnly = listOf(tempTag)
            if (attempts.isEmpty() || attempts.last() != tempOnly) {
                attempts.add(tempOnly)
            }
        }

        val aggregatedRecipes = mutableListOf<Recipe>()
        val seenIds = mutableSetOf<String>()
        for (combo in attempts) {
            val result = queryRecipesWithTags(combo)
            if (result.isNotEmpty()) {
                result.forEach { recipe ->
                    val id = recipe.id
                    if (id != null && seenIds.add(id)) {
                        aggregatedRecipes.add(recipe)
                    }
                }
            }
        }

        binding.recommendationProgressBar.visibility = View.GONE
        if (aggregatedRecipes.isNotEmpty()) {
            binding.recommendationRecyclerView.visibility = View.VISIBLE
            recipeAdapter.updateRecipes(aggregatedRecipes)
        } else {
            binding.recommendationStatusTextView.text = "오늘 날씨에 맞는 추천 레시피를 찾지 못했어요."
            binding.recommendationStatusTextView.visibility = View.VISIBLE
        }
    }

    private suspend fun queryRecipesWithTags(tags: List<String>): List<Recipe> {
        if (tags.isEmpty()) return emptyList()

        return try {
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

    private fun updateWeatherBackground() {
        val weather = currentWeatherData ?: return

        // 현재 시간 (서울 타임존)
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.now(zone)

        // SunCalc로 오늘 일출/일몰 계산 (서울 기준 위/경도)
        val times = SunTimes.compute()
            .on(now)
            .at(SEOUL_LAT, SEOUL_LON)
            .execute()

        val sunrise = times.rise
        val sunset = times.set

        // 지금이 낮인지/밤인지 판별
        val isDay = if (sunrise != null && sunset != null) {
            now.isAfter(sunrise) && now.isBefore(sunset)
        } else {
            // 혹시 null이면 대략 6~18시를 낮으로 처리
            val hour = now.hour
            hour in 6..18
        }

        val ptyCode = weather.pty.toIntOrNull() ?: 0
        val skyCode = weather.sky.toIntOrNull() ?: 0

        val weatherType = when {
            // 비 관련 (1:비, 2:비/눈, 5:빗방울, 6:빗방울/눈날림)
            ptyCode in listOf(1, 2, 5, 6) -> "RAIN"

            // 눈 관련 (3:눈, 7:눈날림)
            ptyCode in listOf(3, 7) -> "SNOW"

            // 강수 없음 + 맑음(sky=1)
            ptyCode == 0 && skyCode == 1 -> "CLEAR"

            // 강수 없음 + 구름많음/흐림(sky=3,4)
            ptyCode == 0 && skyCode in listOf(3, 4) -> "CLOUDY"

            else -> "ETC"
        }

        //  - bgMain: weatherBackgroundContainer 배경
        //  - bgReco: recommendationContainer 배경
        //  - icon:  weathericon 이미지
        val (bgMain, bgReco, icon) = when (weatherType) {
            "RAIN" -> {
                if (isDay) {
                    Triple(
                        R.drawable.bg_w_cloud_b,
                        R.drawable.bg_w_cloud_c,
                        R.drawable.rain,
                    )
                } else {
                    Triple(
                        R.drawable.bg_w_cloud_b,
                        R.drawable.bg_w_cloud_c,
                        R.drawable.rain,
                    )
                }
            }

            "SNOW" -> {
                if (isDay) {
                    Triple(
                        R.drawable.bg_w_day_b,
                        R.drawable.bg_w_day_c,
                        R.drawable.snow
                    )
                } else {
                    Triple(
                        R.drawable.bg_w_night_b,
                        R.drawable.bg_w_night_c,
                        R.drawable.snow
                    )
                }
            }

            "CLEAR" -> {
                if (isDay) {
                    Triple(
                        R.drawable.bg_w_day_b,
                        R.drawable.bg_w_day_c,
                        R.drawable.sun
                    )
                } else {
                    Triple(
                        R.drawable.bg_w_night_b,
                        R.drawable.bg_w_night_c,
                        R.drawable.moon
                    )
                }
            }

            "CLOUDY" -> {
                if (isDay) {
                    Triple(
                        R.drawable.bg_w_day_b,
                        R.drawable.bg_w_day_c,
                        R.drawable.cloudsun
                    )
                } else {
                    Triple(
                        R.drawable.bg_w_day_b,
                        R.drawable.bg_w_day_c,
                        R.drawable.cloudmoon
                    )
                }
            }

            else -> {
                if (isDay) {
                    Triple(
                        R.drawable.bg_w_day_b,
                        R.drawable.bg_w_day_c,
                        R.drawable.cloudsun
                    )
                } else {
                    Triple(
                        R.drawable.bg_w_day_b,
                        R.drawable.bg_w_day_c,
                        R.drawable.cloudmoon
                    )
                }
            }
        }
        binding.weatherBackgroundContainer.setBackgroundResource(bgMain)
        binding.recommendationContainer.setBackgroundResource(bgReco)
        binding.weathericon.setImageResource(icon)
    }
}
