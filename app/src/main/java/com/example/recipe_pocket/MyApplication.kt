package com.example.recipe_pocket

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 카카오 SDK 초기화 (네이티브 앱 키 입력)
        KakaoSdk.init(this, "a0458d62f902a6ce45ed3707a4f5c9d4")
    }
}