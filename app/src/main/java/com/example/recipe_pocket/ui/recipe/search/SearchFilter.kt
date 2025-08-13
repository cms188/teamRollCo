package com.example.recipe_pocket.ui.recipe.search

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class CreationPeriod { TODAY, WEEK, MONTH, YEAR, ALL }
enum class CookingTime { UNDER_10, UNDER_20, UNDER_30, ALL }

@Parcelize
data class SearchFilter(
    val creationPeriod: CreationPeriod = CreationPeriod.ALL,
    val difficulty: Set<String> = emptySet(), // 다중 선택을 위해 Set 사용
    val cookingTime: CookingTime = CookingTime.ALL,
    val categories: Set<String> = emptySet() // 다중 선택을 위해 Set 사용
) : Parcelable {
    companion object {
        fun default(): SearchFilter {
            return SearchFilter()
        }
    }

    // 현재 필터가 기본값과 동일한지 확인하는 함수
    fun isDefault(): Boolean {
        return creationPeriod == CreationPeriod.ALL &&
                difficulty.isEmpty() &&
                cookingTime == CookingTime.ALL &&
                categories.isEmpty()
    }
}