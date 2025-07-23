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

@Parcelize
data class AirQualityData(val pm10: String, val pm25: String, val time: String) : Parcelable

class AirQualityActivity : AppCompatActivity() {

    // URL 인코딩된 서비스 키를 그대로 사용합니다.
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
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            var pm10 = ""
            var pm25 = ""
            var time = ""
            var currentTag: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    XmlPullParser.TEXT -> {
                        when (currentTag) {
                            "pm10Value" -> pm10 = parser.text ?: ""
                            "pm25Value" -> pm25 = parser.text ?: ""
                            "dataTime" -> time = parser.text ?: ""
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item") {
                            if (pm10.isNotEmpty() && pm25.isNotEmpty() && time.isNotEmpty()) {
                                return AirQualityData(pm10, pm25, time)
                            }
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}