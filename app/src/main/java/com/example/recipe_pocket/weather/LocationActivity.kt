package com.example.recipe_pocket.weather

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
                    val shortName = convertAddressToShortName(address)

                    // 위도/경도를 격자 좌표로 변환
                    val grid = GpsConverter.convert(GpsConverter.TO_GRID, location.latitude, location.longitude)

                    val resultIntent = Intent().apply {
                        putExtra("REGION_NAME", shortName)
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

    private suspend fun getAddress(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(this@LocationActivity, Locale.KOREAN)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.getAddressLine(0)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.getAddressLine(0)
                }
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun convertAddressToShortName(fullAddress: String?): String {
        if (fullAddress == null) return "알 수 없음"
        val addressParts = fullAddress.split(" ")
        return when (addressParts.getOrNull(1)) {
            "서울특별시" -> "서울"
            "부산광역시" -> "부산"
            "대구광역시" -> "대구"
            "인천광역시" -> "인천"
            "광주광역시" -> "광주"
            "대전광역시" -> "대전"
            "울산광역시" -> "울산"
            "경기도" -> "경기"
            "강원도", "강원특별자치도" -> "강원"
            "충청북도" -> "충북"
            "충청남도" -> "충남"
            "전라북도", "전북특별자치도" -> "전북"
            "전라남도" -> "전남"
            "경상북도" -> "경북"
            "경상남도" -> "경남"
            "제주특별자치도" -> "제주"
            "세종특별자치시" -> "세종"
            else -> addressParts.getOrNull(1) ?: "알 수 없음"
        }
    }
}