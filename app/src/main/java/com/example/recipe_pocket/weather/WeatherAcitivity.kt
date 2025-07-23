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
        val weatherDataMap = mutableMapOf<String, String>()
        var baseTime = ""

        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentCategory = ""
            var currentFcstValue = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "baseTime" -> baseTime = parser.nextText()
                            "category" -> currentCategory = parser.nextText()
                            "fcstValue" -> currentFcstValue = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            // 하나의 item 파싱이 끝나면, category에 맞는 값을 map에 저장
                            if (currentCategory.isNotEmpty()) {
                                weatherDataMap[currentCategory] = currentFcstValue
                            }
                            currentCategory = ""
                            currentFcstValue = ""
                        }
                    }
                }
                eventType = parser.next()
            }

            // 모든 item을 파싱한 후, map에서 데이터를 꺼내 WeatherData 객체를 생성
            WeatherData(
                baseTime = baseTime,
                tmp = weatherDataMap["TMP"] ?: "N/A",
                pty = weatherDataMap["PTY"] ?: "N/A",
                reh = weatherDataMap["REH"] ?: "N/A",
                sky = weatherDataMap["SKY"] ?: "N/A"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}