import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.recipe_pocket"
    compileSdk = 35

    signingConfigs {
        create("release") {
            if (project.hasProperty("MYAPP_RELEASE_STORE_FILE")) {
                storeFile = file(project.property("MYAPP_RELEASE_STORE_FILE") as String)
                storePassword = project.property("MYAPP_RELEASE_STORE_PASSWORD") as String
                keyAlias = project.property("MYAPP_RELEASE_KEY_ALIAS") as String
                keyPassword = project.property("MYAPP_RELEASE_KEY_PASSWORD") as String
            }
        }
    }

    signingConfigs {
        create("customDebug") {
            storeFile = file(project.property("MYAPP_DEBUG_STORE_FILE") as String)
            storePassword = project.property("MYAPP_DEBUG_STORE_PASSWORD") as String
            keyAlias = project.property("MYAPP_DEBUG_KEY_ALIAS") as String
            keyPassword = project.property("MYAPP_DEBUG_KEY_PASSWORD") as String
        }
    }

    defaultConfig {
        applicationId = "com.example.recipe_pocket"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")

        val weatherServiceKey = localProperties.getProperty("WEATHER_API_KEY") ?: ""
        buildConfigField("String", "WEATHER_API_KEY", "\"$weatherServiceKey\"")

        val airKoreaServiceKey = localProperties.getProperty("AIR_API_KEY") ?: ""
        buildConfigField("String", "AIR_API_KEY", "\"$airKoreaServiceKey\"")

        val kakaoKey = localProperties.getProperty("KAKAO_NATIVE_APP_KEY") ?: ""
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
        manifestPlaceholders["KAKAO_SCHEME"] = "kakao$kakaoKey"
    }



    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("customDebug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.glide)
    implementation(libs.androidx.cardview)
    implementation(libs.firebase.auth)
    //implementation(libs.com.android.legacy.kapt.gradle.plugin)
    implementation("com.google.firebase:firebase-auth:23.2.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation ("com.google.firebase:firebase-auth-ktx:22.3.0")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-storage")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Retrofit 코어 라이브러리
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    // JSON 데이터를 Kotlin 객체로 변환해주는 Gson 컨버터
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // 코루틴 lifecycle-scope 의존성
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // gif 사용
    implementation ("com.github.bumptech.glide:glide:4.16.0")

    // 카카오톡 sdk
    implementation ("com.kakao.sdk:v2-user:2.20.1")
}

