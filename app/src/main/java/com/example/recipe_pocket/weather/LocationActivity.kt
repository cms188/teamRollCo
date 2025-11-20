package com.example.recipe_pocket.weather
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class LocationActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
            getLastKnownLocation()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다. 앱을 다시 시작해주세요.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()
    }
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLastKnownLocation()
        } else {
            locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    private fun getLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                GlobalScope.launch(Dispatchers.Main) {
                    val address = getAddress(location.latitude, location.longitude)
                    val regionInfo = extractRegionInfo(address)
                    // 위도/경도를 격자 좌표로 변환
                    val grid = GpsConverter.convert(GpsConverter.TO_GRID, location.latitude, location.longitude)
                    val resultIntent = Intent().apply {
                        putExtra("REGION_NAME", regionInfo.displayName)
                        putExtra("SIDO_NAME", regionInfo.sidoShortName)
                        putExtra("NX", grid.x)
                        putExtra("NY", grid.y)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            } else {
                Toast.makeText(this, "위치 정보를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                finish()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "위치 정보 로딩에 실패했습니다.", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    private suspend fun getAddress(latitude: Double, longitude: Double): Address? {
        return withContext(Dispatchers.IO) {
            val locales = listOf(Locale.KOREAN, Locale("ko", "KR"), Locale.getDefault())
            for (locale in locales) {
                try {
                    val geocoder = Geocoder(this@LocationActivity, locale)
                    val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    }
                    if (address != null) {
                        return@withContext address
                    }
                } catch (e: IOException) {
                    // try next locale
                }
            }
            null
        }
    }
    private data class RegionInfo(
        val sidoShortName: String,
        val displayName: String
    )

    

private fun extractRegionInfo(address: Address?): RegionInfo {
    val unknown = "알 수 없음"
    if (address == null) {
        return RegionInfo(unknown, unknown)
    }

    fun sanitize(token: String?): String? {
        return token
            ?.replace("대한민국", "")
            ?.replace("KR", "", ignoreCase = true)
            ?.replace(",", " ")
            ?.replace('-', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun normalizeProvince(raw: String?): Pair<String, String>? {
        val trimmed = sanitize(raw) ?: return null
        val key = trimmed.lowercase(Locale.ROOT).replace(" ", "")
        return when (key) {
            "서울특별시", "서울시", "서울", "seoul" -> "서울특별시" to "서울"
            "부산광역시", "부산시", "부산", "busan" -> "부산광역시" to "부산"
            "대구광역시", "대구시", "대구", "daegu" -> "대구광역시" to "대구"
            "인천광역시", "인천시", "인천", "incheon" -> "인천광역시" to "인천"
            "광주광역시", "광주시", "광주", "gwangju" -> "광주광역시" to "광주"
            "대전광역시", "대전시", "대전", "daejeon" -> "대전광역시" to "대전"
            "울산광역시", "울산시", "울산", "ulsan" -> "울산광역시" to "울산"
            "경기도", "경기", "gyeonggi", "gyeonggido" -> "경기도" to "경기"
            "강원도", "강원특별자치도", "강원", "gangwon", "gangwondo" -> "강원특별자치도" to "강원"
            "충청북도", "충북", "chungcheongbukdo", "chungbuk" -> "충청북도" to "충북"
            "충청남도", "충남", "chungcheongnamdo", "chungnam" -> "충청남도" to "충남"
            "전라북도", "전북특별자치도", "전북", "jeollabukdo", "jeonbuk" -> "전북특별자치도" to "전북"
            "전라남도", "전남", "jeollanamdo", "jeonnam" -> "전라남도" to "전남"
            "경상북도", "경북", "gyeongsangbukdo", "gyeongbuk" -> "경상북도" to "경북"
            "경상남도", "경남", "gyeongsangnamdo", "gyeongnam" -> "경상남도" to "경남"
            "제주특별자치도", "제주도", "제주", "jeju", "jejudo" -> "제주특별자치도" to "제주"
            "세종특별자치시", "세종시", "세종", "sejong" -> "세종특별자치시" to "세종"
            else -> null
        }
    }

    val tokens = mutableListOf<String>()
    sanitize(address.adminArea)?.let { tokens += it }
    sanitize(address.subAdminArea)?.let { tokens += it }
    sanitize(address.locality)?.let { tokens += it }
    sanitize(address.subLocality)?.let { tokens += it }
    sanitize(address.thoroughfare)?.let { tokens += it }
    address.getAddressLine(0)?.let { line ->
        line.split(' ').mapNotNull(::sanitize).forEach { tokens += it }
    }

    if (tokens.isEmpty()) {
        return RegionInfo(unknown, address.getAddressLine(0) ?: unknown)
    }

    val provincePair = tokens.asSequence().mapNotNull(::normalizeProvince).firstOrNull()
    val provinceDisplay = provincePair?.first
        ?: tokens.firstOrNull { it.endsWith("도") || it.endsWith("시") }
        ?: unknown

    val cityDisplay = listOfNotNull(
        sanitize(address.locality),
        sanitize(address.subAdminArea),
        sanitize(address.subLocality),
        tokens.firstOrNull { it != provinceDisplay && (it.endsWith("시") || it.endsWith("군") || it.endsWith("구")) }
    ).firstOrNull()

    val displayName = listOfNotNull(
        provinceDisplay.takeUnless { it == unknown },
        cityDisplay
    ).joinToString(" ").ifBlank { address.getAddressLine(0) ?: unknown }

    val shortName = provincePair?.second ?: when (provinceDisplay) {
        "서울특별시" -> "서울"
        "부산광역시" -> "부산"
        "대구광역시" -> "대구"
        "인천광역시" -> "인천"
        "광주광역시" -> "광주"
        "대전광역시" -> "대전"
        "울산광역시" -> "울산"
        "경기도" -> "경기"
        "강원특별자치도", "강원도" -> "강원"
        "충청북도" -> "충북"
        "충청남도" -> "충남"
        "전북특별자치도", "전라북도" -> "전북"
        "전라남도" -> "전남"
        "경상북도" -> "경북"
        "경상남도" -> "경남"
        "제주특별자치도" -> "제주"
        "세종특별자치시" -> "세종"
        else -> unknown
    }

    return RegionInfo(
        sidoShortName = shortName,
        displayName = displayName
    )
}




}
