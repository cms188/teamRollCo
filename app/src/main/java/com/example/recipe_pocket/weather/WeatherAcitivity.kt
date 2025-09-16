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

@Parcelize
data class WeatherData(
    val baseTime: String,
    var tmp: String,
    val pty: String, // precipitation type
    val reh: String, // humidity
    val sky: String, // sky condition
    val pop: String  // precipitation probability (%)
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
            urlBuilder.append("&${URLEncoder.encode("numOfRows", "UTF-8")}=100")
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

        val nowFormat = SimpleDateFormat("HHmm", Locale.KOREAN).apply { timeZone = koreaTimeZone }
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREAN).apply { timeZone = koreaTimeZone }

        val now = nowFormat.format(calendar.time)

        val baseTimes = listOf("0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300")

        val latestBaseTime = baseTimes.lastOrNull { it <= now }

        return if (latestBaseTime != null) {
            Pair(dateFormat.format(calendar.time), latestBaseTime)
        } else {
            calendar.add(Calendar.DATE, -1)
            Pair(dateFormat.format(calendar.time), baseTimes.last())
        }
    }

    private fun parseWeatherXml(xmlString: String): WeatherData? {
        val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val calendar = Calendar.getInstance(koreaTimeZone)
        val currentHour = SimpleDateFormat("HH00", Locale.KOREAN).apply {
            timeZone = koreaTimeZone
        }.format(calendar.time)

        val currentDate = SimpleDateFormat("yyyyMMdd", Locale.KOREAN).apply {
            timeZone = koreaTimeZone
        }.format(calendar.time)

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
                            currentCategory = ""
                            currentFcstValue = ""
                            currentFcstDate = ""
                            currentFcstTime = ""
                        }
                    }
                }
                eventType = parser.next()
            }

            val targetDateTime = currentDate + currentHour

            var selectedItems = weatherItems.filter {
                it.fcstDate + it.fcstTime == targetDateTime
            }

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

            if (selectedItems.isEmpty() && weatherItems.isNotEmpty()) {
                val firstTime = weatherItems.minByOrNull { it.fcstDate + it.fcstTime }
                    ?.let { it.fcstDate + it.fcstTime }
                selectedItems = weatherItems.filter {
                    it.fcstDate + it.fcstTime == firstTime
                }
            }

            if (selectedItems.isNotEmpty()) {
                val weatherMap = mutableMapOf<String, String>()
                selectedItems.forEach { item ->
                    weatherMap[item.category] = item.fcstValue
                }

                val actualFcstTime = selectedItems.firstOrNull()?.fcstTime ?: ""

                return WeatherData(
                    baseTime = "$baseTime (예보시간: $actualFcstTime)",
                    tmp = weatherMap["TMP"] ?: weatherMap["T1H"] ?: "N/A",
                    pty = weatherMap["PTY"] ?: "0",
                    reh = weatherMap["REH"] ?: "N/A",
                    sky = weatherMap["SKY"] ?: "N/A",
                    pop = weatherMap["POP"] ?: "N/A"
                )
            }

            return null

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

