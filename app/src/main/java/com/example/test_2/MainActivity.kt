package com.example.test_2

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View // findViewById의 반환 타입을 View로 받을 때 사용 가능
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
// import android.widget.LinearLayout // 기존 코드에서 사용. View로 받는 것이 더 유연할 수 있음
// import androidx.cardview.widget.CardView // CardView ID로 찾을 때 필요
import androidx.viewpager2.widget.ViewPager2 // ViewPager2 import
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // activity_main.xml 또는 사용 중인 메인 레이아웃 파일


        // --- 기존 HorizontalScrollView의 아이템 처리 ---
        // <include> 태그에 직접 ID를 부여한 경우, findViewById는 해당 <include>된 레이아웃의 루트 View를 반환합니다.
        // 해당 루트 View 내에서 다시 findViewById를 호출하여 내부 요소를 찾아야 합니다.

        try { // ID를 못 찾거나 타입 캐스팅 오류 방지를 위해 try-catch 사용
            val popularItem1View = findViewById<View>(R.id.hot_cook_1) // cook_card_01을 include
            val imageView1 = popularItem1View.findViewById<ImageView>(R.id.recipe_image_view)
            imageView1.setImageResource(R.drawable.testimg1) // testimg1.jpg 또는 .png 파일이 res/drawable에 있어야 함
            // imageView1.background = null // 필요시 배경 제거

            val popularItem2View = findViewById<View>(R.id.hot_cook_2) // cook_card_01을 include
            val imageView2 = popularItem2View.findViewById<ImageView>(R.id.recipe_image_view)
            imageView2.setImageResource(R.drawable.testimg2) // testimg2.jpg 또는 .png 파일이 res/drawable에 있어야 함
            // imageView2.background = null // 필요시 배경 제거

            // --- 아이템 2 처리 (popular_item_4, popular_item_5는 cook_card_02 레이아웃을 사용한다고 가정) ---
            val popularItem4View = findViewById<View>(R.id.pick_cook_1) // cook_card_02를 include
            val imageView4 = popularItem4View.findViewById<ImageView>(R.id.recipe_image_view) // cook_card_02 내부의 recipe_image_view
            imageView4.setImageResource(R.drawable.testimg1)
            // imageView4.background = null // 필요시 배경 제거

            val popularItem5View = findViewById<View>(R.id.pick_cook_2) // cook_card_02를 include
            val imageView5 = popularItem5View.findViewById<ImageView>(R.id.recipe_image_view)
            imageView5.setImageResource(R.drawable.testimg2)
            // imageView5.background = null // 필요시 배경 제거

            val ncookit1 = findViewById<View>(R.id.n_cook_1)
            val ncookiv1 = ncookit1.findViewById<ImageView>(R.id.recipe_image_view)
            ncookiv1.setImageResource(R.drawable.testimg1)

            val ncookit2 = findViewById<View>(R.id.n_cook_2)
            val ncookiv2 = ncookit2.findViewById<ImageView>(R.id.recipe_image_view)
            ncookiv2.setImageResource(R.drawable.testimg2)

        } catch (e: Exception) {
            // 예: ID를 찾지 못했거나, null 참조 등
            e.printStackTrace()
            // 실제 앱에서는 사용자에게 오류를 알리거나 로그를 남기는 등의 처리가 필요할 수 있습니다.
        }


        // --- ViewPager2 설정 ---
        val viewPager = findViewById<ViewPager2>(R.id.cook_tips_view_pager) // activity_main.xml에 정의된 ViewPager2의 ID

        // ViewPager2에 표시할 샘플 데이터 생성
        val cookTipItems = listOf(
            CookTipItem("오늘의 추천 요리팁!", "재료 손질부터 플레이팅까지", R.drawable.testimg1), // 예시 이미지
            CookTipItem("간단한 밑반찬 만들기", "냉장고를 든든하게 채워요", R.drawable.testimg2),
            CookTipItem("특별한 날 홈파티 메뉴", "쉽고 근사하게 준비하기", R.drawable.testimg1) // 다른 이미지 리소스 사용 가능
            // 필요한 만큼 아이템 추가
        )

        val cookTipAdapter = CookTipAdapter(cookTipItems)
        viewPager.adapter = cookTipAdapter

        // (선택 사항) ViewPager2 페이지 간 간격 및 다음/이전 페이지 살짝 보이게 하는 효과
        // 페이지 간 간격을 주려면, cook_tip_01.xml의 루트 레이아웃이나 내부 컨테이너에 margin을 주거나,
        // ViewPager2의 PageTransformer를 사용하여 복잡한 효과를 줄 수 있습니다.
        // 가장 간단한 방법은 cook_tip_01.xml 내부의 recipe_image_container에 좌우 마진을 주는 것입니다 (이전 XML 답변에서 제안).
        // 예: android:layout_marginStart="16dp", android:layout_marginEnd="16dp"

        // viewPager.offscreenPageLimit = 1 // 현재 페이지 양 옆으로 몇 페이지를 미리 로드할지 설정 (기본값은 1)

        val userPageButton = findViewById<ImageButton>(R.id.nav_item_5) //하단 5번째 버튼
        userPageButton.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // 로그인된 사용자라면 UserPageActivity로 이동
                val intent = Intent(this, UserPageActivity::class.java)
                startActivity(intent)
            } else {
                // 로그인되지 않은 사용자라면 LoginActivity로 이동
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
        }

    }
}