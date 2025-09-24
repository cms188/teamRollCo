package com.example.recipe_pocket

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.navercorp.nid.NaverIdLoginSDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 카카오 SDK 초기화 (네이티브 앱 키 입력)
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        NaverIdLoginSDK.initialize(this, BuildConfig.NAVER_CLIENT_ID, BuildConfig.NAVER_CLIENT_SECRET, BuildConfig.NAVER_CLIENT_NAME)
        NaverIdLoginSDK.showDevelopersLog(BuildConfig.DEBUG)
    }
}
