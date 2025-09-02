package com.example.recipe_pocket.ui.user

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class RecentlyViewedRecipe(
    val recipeId: String,
    val title: String,
    val thumbnailUrl: String?,
    val viewedAt: Long = System.currentTimeMillis()
)

object RecentlyViewedManager {
    private const val PREFS_NAME = "recently_viewed_prefs"
    private const val KEY_RECENTLY_VIEWED = "recently_viewed_recipes"
    private const val MAX_RECENT_ITEMS = 20

    private val gson = Gson()

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 최근 본 레시피 추가
     */
    fun addRecentlyViewed(context: Context, recipeId: String, title: String, thumbnailUrl: String?) {
        val prefs = getSharedPreferences(context)
        val recentList = getRecentlyViewed(context).toMutableList()

        // 이미 있는 레시피면 제거 (최신으로 업데이트하기 위해)
        recentList.removeAll { it.recipeId == recipeId }

        // 맨 앞에 추가
        recentList.add(0, RecentlyViewedRecipe(recipeId, title, thumbnailUrl))

        // 최대 개수 유지
        val trimmedList = if (recentList.size > MAX_RECENT_ITEMS) {
            recentList.subList(0, MAX_RECENT_ITEMS)
        } else {
            recentList
        }

        // 저장
        val json = gson.toJson(trimmedList)
        prefs.edit().putString(KEY_RECENTLY_VIEWED, json).apply()
    }

    /**
     * 최근 본 레시피 목록 가져오기
     */
    fun getRecentlyViewed(context: Context): List<RecentlyViewedRecipe> {
        val prefs = getSharedPreferences(context)
        val json = prefs.getString(KEY_RECENTLY_VIEWED, null)

        return if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            val type = object : TypeToken<List<RecentlyViewedRecipe>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        }
    }

    /**
     * 특정 레시피 삭제
     */
    fun removeRecentlyViewed(context: Context, recipeId: String) {
        val prefs = getSharedPreferences(context)
        val recentList = getRecentlyViewed(context).toMutableList()

        recentList.removeAll { it.recipeId == recipeId }

        val json = gson.toJson(recentList)
        prefs.edit().putString(KEY_RECENTLY_VIEWED, json).apply()
    }

    /**
     * 전체 삭제
     */
    fun clearAll(context: Context) {
        val prefs = getSharedPreferences(context)
        prefs.edit().remove(KEY_RECENTLY_VIEWED).apply()
    }
}