package com.example.recipe_pocket.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
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
    val pty: String,
    val reh: String,
    val sky: String,
    val pop: String
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
            Log.d("WeatherActivity", "fetchWeatherData nx=$nx ny=$ny")
            val (baseDate, baseTime) = getBaseTime()
            val urlBuilder = StringBuilder("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst")
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
                    parseWeatherXml(conn.inputStream.bufferedReader().readText(), baseDate, baseTime, nx, ny)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun fetchForecastSkyPop(nx: Int, ny: Int, targetKey: String): Map<String, String>? {
        val (baseDate, baseTime) = getBaseTimeSkyPop()
        val urlBuilder = StringBuilder("https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst")
        urlBuilder.append("?${URLEncoder.encode("serviceKey", "UTF-8")}=$weatherServiceKey")
        urlBuilder.append("&${URLEncoder.encode("pageNo", "UTF-8")}=1")
        urlBuilder.append("&${URLEncoder.encode("numOfRows", "UTF-8")}=100")
        urlBuilder.append("&${URLEncoder.encode("dataType", "UTF-8")}=XML")
        urlBuilder.append("&${URLEncoder.encode("base_date", "UTF-8")}=${URLEncoder.encode(baseDate, "UTF-8")}")
        urlBuilder.append("&${URLEncoder.encode("base_time", "UTF-8")}=${URLEncoder.encode(baseTime, "UTF-8")}")
        urlBuilder.append("&${URLEncoder.encode("nx", "UTF-8")}=${URLEncoder.encode(nx.toString(), "UTF-8")}")
        urlBuilder.append("&${URLEncoder.encode("ny", "UTF-8")}=${URLEncoder.encode(ny.toString(), "UTF-8")}")

        return try {
            val url = URL(urlBuilder.toString())
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Content-type", "application/json")

            if (conn.responseCode in 200..300) {
                parseForecastXml(conn.inputStream.bufferedReader().readText(), targetKey)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getBaseTime(): Pair<String, String> {
        val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val calendar = Calendar.getInstance(koreaTimeZone)

        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREAN).apply { timeZone = koreaTimeZone }
        val hourFormat = SimpleDateFormat("HH", Locale.KOREAN).apply { timeZone = koreaTimeZone }

        val minute = calendar.get(Calendar.MINUTE)
        if (minute < 10) {
            calendar.add(Calendar.HOUR_OF_DAY, -1)
        }

        val baseDate = dateFormat.format(calendar.time)
        val baseTime = hourFormat.format(calendar.time) + "00"

        return Pair(baseDate, baseTime)
    }

    private fun getBaseTimeSkyPop(): Pair<String, String> {
        val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val calendar = Calendar.getInstance(koreaTimeZone)

        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREAN).apply { timeZone = koreaTimeZone }
        val minute = calendar.get(Calendar.MINUTE)
        if (minute < 10) {
            calendar.add(Calendar.HOUR_OF_DAY, -1)
        }
        val hourFormat = SimpleDateFormat("HH", Locale.KOREAN).apply { timeZone = koreaTimeZone }
        val hour = hourFormat.format(calendar.time).toInt()

        val baseHours = listOf("02", "05", "08", "11", "14", "17", "20", "23")
        val baseHour = baseHours.map { it.toInt() }.filter { it <= hour }.maxOrNull() ?: run {
            calendar.add(Calendar.DATE, -1)
            baseHours.last().toInt()
        }

        val baseDate = dateFormat.format(calendar.time)
        val baseTime = String.format(Locale.KOREAN, "%02d00", baseHour)

        return Pair(baseDate, baseTime)
    }

    private fun findClosestKey(keys: Set<String>, targetKey: String): String? {
        if (keys.isEmpty()) return null
        val nextOrSame = keys.filter { it >= targetKey }.minOrNull()
        return nextOrSame ?: keys.maxOrNull()
    }

    private fun parseForecastXml(xmlString: String, targetKey: String): Map<String, String>? {
        val wantedCategories = setOf("SKY", "POP")
        val dataByKey = mutableMapOf<String, MutableMap<String, String>>()

        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentTag: String? = null
            var fcstDate = ""
            var fcstTime = ""
            var category = ""
            var value = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTag = parser.name
                    XmlPullParser.TEXT -> when (currentTag) {
                        "fcstDate" -> fcstDate = parser.text ?: ""
                        "fcstTime" -> fcstTime = parser.text ?: ""
                        "category" -> category = parser.text ?: ""
                        "fcstValue" -> value = parser.text ?: ""
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            if (category in wantedCategories && fcstDate.isNotEmpty() && fcstTime.isNotEmpty() && value.isNotEmpty() && value != "-") {
                                val key = fcstDate + fcstTime
                                val bucket = dataByKey.getOrPut(key) { mutableMapOf() }
                                if (!bucket.containsKey(category)) {
                                    bucket[category] = value
                                }
                            }
                            fcstDate = ""
                            fcstTime = ""
                            category = ""
                            value = ""
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }

            val closestKey = findClosestKey(dataByKey.keys, targetKey) ?: return null
            val bucket = dataByKey[closestKey] ?: return null
            val result = mutableMapOf<String, String>()
            wantedCategories.forEach { cat ->
                bucket[cat]?.let { result[cat] = it }
            }
            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseWeatherXml(xmlString: String, expectedDate: String, expectedTime: String, nx: Int, ny: Int): WeatherData? {
        val observationMap = mutableMapOf<String, MutableMap<String, String>>()

        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentTag: String? = null
            var baseDate = ""
            var baseTime = ""
            var category = ""
            var obsrValue = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    XmlPullParser.TEXT -> {
                        when (currentTag) {
                            "baseDate" -> baseDate = parser.text ?: ""
                            "baseTime" -> baseTime = parser.text ?: ""
                            "category" -> category = parser.text ?: ""
                            "obsrValue" -> obsrValue = parser.text ?: ""
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            if (baseDate.isNotEmpty() && baseTime.isNotEmpty() &&
                                category.isNotEmpty() && obsrValue.isNotEmpty() && obsrValue != "-") {
                                val key = baseDate + baseTime
                                val dataForTime = observationMap.getOrPut(key) { mutableMapOf() }
                                dataForTime[category] = obsrValue
                            }
                            baseDate = ""
                            baseTime = ""
                            category = ""
                            obsrValue = ""
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }

            val expectedKey = expectedDate + expectedTime
            val selectedKey = findClosestKey(observationMap.keys, expectedKey) ?: return null

            val selectedData = observationMap[selectedKey]?.toMutableMap() ?: return null
            fetchForecastSkyPop(nx, ny, selectedKey)?.let { forecastValues ->
                selectedData.putAll(forecastValues)
            }

            val selectedDate = selectedKey.take(8)
            val selectedTime = selectedKey.drop(8)

            WeatherData(
                baseTime = "$selectedDate $selectedTime",
                tmp = selectedData["T1H"] ?: "N/A",
                pty = selectedData["PTY"] ?: "0",
                reh = selectedData["REH"] ?: "N/A",
                sky = selectedData["SKY"] ?: "N/A",
                pop = selectedData["POP"] ?: "N/A"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

