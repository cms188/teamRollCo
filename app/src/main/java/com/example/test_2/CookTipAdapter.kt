 package com.example.test_2

 import android.view.LayoutInflater
 import android.view.View
 import android.view.ViewGroup
 import android.widget.ImageView
 import android.widget.TextView
 import androidx.recyclerview.widget.RecyclerView

 class CookTipAdapter(private val items: List<CookTipItem>) :
     RecyclerView.Adapter<CookTipAdapter.CookTipViewHolder>() {

     override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CookTipViewHolder {
         // cook_tip_01.xml을 인플레이트합니다.
         val view = LayoutInflater.from(parent.context)
             .inflate(R.layout.cook_tip_01, parent, false)
         return CookTipViewHolder(view)
     }

     override fun onBindViewHolder(holder: CookTipViewHolder, position: Int) {
         val item = items[position]
         holder.bind(item)
     }

     override fun getItemCount(): Int = items.size

     inner class CookTipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
         // cook_tip_01.xml 내부의 View들을 findViewById로 찾습니다.
         private val mainTextView: TextView = itemView.findViewById(R.id.main_content_text)
         private val subTextView: TextView = itemView.findViewById(R.id.sub_content_text)
         private val imageView: ImageView = itemView.findViewById(R.id.recipe_image_view)

         fun bind(cookTip: CookTipItem) {
             mainTextView.text = cookTip.mainText
             subTextView.text = cookTip.subText
             imageView.setImageResource(cookTip.imageResId)
             // 만약 cook_tip_01.xml의 ImageView에 설정된 background (@drawable/rounded_gray_background)를
             // 이미지가 로드된 후 제거하고 싶다면 아래 주석을 해제하세요.
             // imageView.background = null
         }
     }
 }