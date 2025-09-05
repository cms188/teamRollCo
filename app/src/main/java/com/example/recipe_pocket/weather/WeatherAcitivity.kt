package com.example.recipe_pocket.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.recipe_pocket.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// 데이터를 담을 클래스
@Parcelize
data class WeatherData(
    val baseTime: String,
    var tmp: String,
    val pty: String, // 강수형태
    val reh: String, // 습도
    val sky: String  // 하늘상태
) : Parcelable

class WeatherActivity : AppCompatActivity() {

    private val weatherServiceKey = BuildConfig.WEATHER_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nx = intent.getIntExtra("NX", 0)
        val ny = intent.getIntExtra("NY", 0)

        lifecycleScope.launch {
            val weatherData = fetchWeatherData(nx, ny)
            val resultIntent = Intent().apply {
                if (weatherData != null) {
                    putExtra("WEATHER_DATA", weatherData)
                }
            }
            setResult(if (weatherData != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultIntent)
            finish()
        }
    }

    private suspend fun fetchWeatherData(nx: Int, ny: Int): WeatherData? {
        return withContext(Dispatchers.IO) {
            val (baseDate, baseTime) = getBaseTime()
            val urlBuilder = StringBuilder("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst")
            urlBuilder.append("?${URLEncoder.encode("serviceKey", "UTF-8")}=$weatherServiceKey")
            urlBuilder.append("&${URLEncoder.encode("pageNo", "UTF-8")}=1")
            urlBuilder.append("&${URLEncoder.encode("numOfRows", "UTF-8")}=100") // 충분한 데이터를 가져오도록 설정
            urlBuilder.append("&${URLEncoder.encode("dataType", "UTF-8")}=XML")
            urlBuilder.append("&${URLEncoder.encode("base_date", "UTF-8")}=${URLEncoder.encode(baseDate, "UTF-8")}")
            urlBuilder.append("&${URLEncoder.encode("base_time", "UTF-8")}=${URLEncoder.encode(baseTime, "UTF-8")}")
            urlBuilder.append("&${URLEncoder.encode("nx", "UTF-8")}=${URLEncoder.encode(nx.toString(), "UTF-8")}")
            urlBuilder.append("&${URLEncoder.encode("ny", "UTF-8")}=${URLEncoder.encode(ny.toString(), "UTF-8")}")

            try {
                val url = URL(urlBuilder.toString())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Content-type", "application/json")

                if (conn.responseCode in 200..300) {
                    parseWeatherXml(conn.inputStream.bufferedReader().readText())
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun getBaseTime(): Pair<String, String> {
        val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val calendar = Calendar.getInstance(koreaTimeZone)

        // 2. SimpleDateFormat 에도 KST를 적용하여 포매팅 오류를 방지합니다.
        val nowFormat = SimpleDateFormat("HHmm", Locale.KOREAN).apply { timeZone = koreaTimeZone }
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREAN).apply { timeZone = koreaTimeZone }

        val now = nowFormat.format(calendar.time)

        val baseTimes = listOf("0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300")

        val latestBaseTime = baseTimes.lastOrNull { it <= now }

        return if (latestBaseTime != null) {
            Pair(dateFormat.format(calendar.time), latestBaseTime)
        } else {
            // 자정을 넘은 경우, 어제 날짜의 마지막 발표 시각을 사용
            calendar.add(Calendar.DATE, -1)
            Pair(dateFormat.format(calendar.time), baseTimes.last())
        }
    }

    private fun parseWeatherXml(xmlString: String): WeatherData? {
        // 현재 한국 시간 가져오기
        val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val calendar = Calendar.getInstance(koreaTimeZone)
        val currentHour = SimpleDateFormat("HH00", Locale.KOREAN).apply {
            timeZone = koreaTimeZone
        }.format(calendar.time)

        // 날짜 포맷 (fcstDate 비교용)
        val currentDate = SimpleDateFormat("yyyyMMdd", Locale.KOREAN).apply {
            timeZone = koreaTimeZone
        }.format(calendar.time)

        // 여러 시간대의 데이터를 저장할 리스트
        data class WeatherItem(
            val fcstDate: String,
            val fcstTime: String,
            val category: String,
            val fcstValue: String
        )

        val weatherItems = mutableListOf<WeatherItem>()
        var baseTime = ""

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentCategory = ""
            var currentFcstValue = ""
            var currentFcstDate = ""
            var currentFcstTime = ""

            // 모든 item 파싱
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "baseTime" -> baseTime = parser.nextText()
                            "category" -> currentCategory = parser.nextText()
                            "fcstValue" -> currentFcstValue = parser.nextText()
                            "fcstDate" -> currentFcstDate = parser.nextText()
                            "fcstTime" -> currentFcstTime = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            weatherItems.add(
                                WeatherItem(
                                    fcstDate = currentFcstDate,
                                    fcstTime = currentFcstTime,
                                    category = currentCategory,
                                    fcstValue = currentFcstValue
                                )
                            )
                            // 초기화
                            currentCategory = ""
                            currentFcstValue = ""
                            currentFcstDate = ""
                            currentFcstTime = ""
                        }
                    }
                }
                eventType = parser.next()
            }

            // 현재 시간과 가장 가까운 데이터 찾기
            val targetDateTime = currentDate + currentHour

            // 1. 먼저 현재 시간과 정확히 일치하는 데이터 찾기
            var selectedItems = weatherItems.filter {
                it.fcstDate + it.fcstTime == targetDateTime
            }

            // 2. 없으면 현재 시간 이후의 가장 가까운 데이터 찾기
            if (selectedItems.isEmpty()) {
                val futureItems = weatherItems.filter {
                    (it.fcstDate + it.fcstTime) > targetDateTime
                }.sortedBy { it.fcstDate + it.fcstTime }

                if (futureItems.isNotEmpty()) {
                    val nearestTime = futureItems.first().let { it.fcstDate + it.fcstTime }
                    selectedItems = weatherItems.filter {
                        it.fcstDate + it.fcstTime == nearestTime
                    }
                }
            }

            // 3. 그래도 없으면 가장 첫 번째 시간대의 데이터 사용
            if (selectedItems.isEmpty() && weatherItems.isNotEmpty()) {
                val firstTime = weatherItems.minByOrNull { it.fcstDate + it.fcstTime }
                    ?.let { it.fcstDate + it.fcstTime }
                selectedItems = weatherItems.filter {
                    it.fcstDate + it.fcstTime == firstTime
                }
            }

            // 선택된 시간대의 데이터로 WeatherData 생성
            if (selectedItems.isNotEmpty()) {
                val weatherMap = mutableMapOf<String, String>()
                selectedItems.forEach { item ->
                    weatherMap[item.category] = item.fcstValue
                }

                // 실제 예보 시간 정보도 포함 (디버깅/표시용)
                val actualFcstTime = selectedItems.firstOrNull()?.fcstTime ?: ""

                return WeatherData(
                    baseTime = "$baseTime (예보시간: $actualFcstTime)",  // 실제 사용되는 예보 시간 표시
                    tmp = weatherMap["TMP"] ?: weatherMap["T1H"] ?: "N/A",  // TMP 또는 T1H (1시간 기온)
                    pty = weatherMap["PTY"] ?: "0",  // 강수형태 (없으면 0)
                    reh = weatherMap["REH"] ?: "N/A",  // 습도
                    sky = weatherMap["SKY"] ?: "N/A"   // 하늘상태
                )
            }

            return null

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}