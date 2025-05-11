============drawable============

bookmark_background_shape_figma.xml
 - 북마크 아이콘을 둥글게 만들어줌

ic_bookmark_outline_figma.xml
  - 북마크 아이콘. figma에서 가져옴

circle_gray_background.xml
 - 작성자 프로필를 둥글게 만들어줌

rounded_gray_background.xml
 - 오늘의 인기 요리 이미지를 둥글게 만들어줌

rounded_top_white_background.xml
 - 메인 화면의 상단을 둥글게 만들어줌

 ===============================

androidx.cardview:cardview 의존성 추가? - 안하고도 됐음
안드로이드에
buildFeatures {
        viewBinding = true
    } 추가
implementation(libs.androidx.viewpager2)
implementation(libs.glide)
implementation(libs.com.android.legacy.kapt.gradle.plugin) // 이건 삭제 오류났었음
implementation(libs.androidx.cardview)

androidmanifest
application과 activity에  android:hardwareAccelerated="true" 추가
