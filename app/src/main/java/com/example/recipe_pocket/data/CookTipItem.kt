package com.example.recipe_pocket.data

data class CookTipItem(
    val mainText: String,
    val subText: String,
    val imageResId: Int // 로컬 드로어블 리소스를 사용한다고 가정
)