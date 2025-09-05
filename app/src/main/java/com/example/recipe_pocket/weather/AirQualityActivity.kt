package com.example.recipe_pocket.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Parcelable
import com.example.recipe_pocket.BuildConfig
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
data class AirQualityData(val pm10: String, val pm25: String, val time: String) : Parcelable

class AirQualityActivity : AppCompatActivity() {

    private val airKoreaServiceKey = BuildConfig.AIR_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val regionName = intent.getStringExtra("REGION_NAME") ?: "알 수 없음"

        lifecycleScope.launch {
            val airData = fetchAirQualityData(regionName)
            val resultIntent = Intent().apply {
                putExtra("REGION_NAME", regionName)
                putExtra("AIR_QUALITY_DATA", airData)
            }
            setResult(if (airData != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultIntent)
            finish()
        }
    }

    private suspend fun fetchAirQualityData(sidoName: String): AirQualityData? {
        return withContext(Dispatchers.IO) {
            val urlBuilder = StringBuilder("https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty")

            urlBuilder.append("?serviceKey=$airKoreaServiceKey")
            urlBuilder.append("&${URLEncoder.encode("returnType", "UTF-8")}=xml")
            urlBuilder.append("&${URLEncoder.encode("sidoName", "UTF-8")}=${URLEncoder.encode(sidoName, "UTF-8")}")
            urlBuilder.append("&${URLEncoder.encode("ver", "UTF-8")}=1.0")

            try {
                val url = URL(urlBuilder.toString())
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"

                if (conn.responseCode in 200..300) {
                    val xmlString = conn.inputStream.bufferedReader().readText()
                    parseXml(xmlString)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun parseXml(xmlString: String): AirQualityData? {
        val koreaTimeZone = TimeZone.getTimeZone("Asia/Seoul")
        val calendar = Calendar.getInstance(koreaTimeZone)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:00", Locale.KOREAN).apply {
            timeZone = koreaTimeZone
        }
        val currentTimeStr = dateFormat.format(calendar.time)

        val airQualityDataMap = mutableMapOf<String, MutableList<Triple<String, String, String>>>()

        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var currentStationName = ""
            var currentPm10 = ""
            var currentPm25 = ""
            var currentDataTime = ""
            var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    XmlPullParser.TEXT -> {
                        when (currentTag) {
                            "stationName" -> currentStationName = parser.text ?: ""
                            "pm10Value" -> currentPm10 = parser.text ?: ""
                            "pm25Value" -> currentPm25 = parser.text ?: ""
                            "dataTime" -> currentDataTime = parser.text ?: ""
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            // 유효한 데이터만 저장
                            if (currentPm10.isNotEmpty() && currentPm10 != "-" &&
                                currentPm25.isNotEmpty() && currentPm25 != "-" &&
                                currentDataTime.isNotEmpty()) {

                                if (!airQualityDataMap.containsKey(currentDataTime)) {
                                    airQualityDataMap[currentDataTime] = mutableListOf()
                                }
                                airQualityDataMap[currentDataTime]?.add(
                                    Triple(currentStationName, currentPm10, currentPm25)
                                )
                            }
                            // 초기화
                            currentStationName = ""
                            currentPm10 = ""
                            currentPm25 = ""
                            currentDataTime = ""
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }

            val sortedTimes = airQualityDataMap.keys.sorted()

            var selectedTime = sortedTimes.find { it == currentTimeStr }

            if (selectedTime == null) {
                selectedTime = sortedTimes.filter { it <= currentTimeStr }.maxOrNull()
            }

            if (selectedTime == null && sortedTimes.isNotEmpty()) {
                selectedTime = sortedTimes.last()
            }

            selectedTime?.let { time ->
                val stations = airQualityDataMap[time] ?: return null

                val validStations = stations.filter { (_, pm10, pm25) ->
                    pm10.toIntOrNull() != null && pm25.toIntOrNull() != null
                }

                if (validStations.isNotEmpty()) {
                    val avgPm10 = validStations.map { it.second.toInt() }.average().toInt()
                    val avgPm25 = validStations.map { it.third.toInt() }.average().toInt()

                    return AirQualityData(
                        pm10 = avgPm10.toString(),
                        pm25 = avgPm25.toString(),
                        time = time
                    )
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}