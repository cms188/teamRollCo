package com.example.recipe_pocket.ui.recipe.search

import android.content.Context
import android.content.SharedPreferences

object SearchHistoryManager {

    private const val PREFS_NAME = "search_history_prefs"
    private const val KEY_SEARCH_HISTORY = "key_search_history_string" // 키 이름 변경
    private const val MAX_HISTORY_SIZE = 10
    private const val SEPARATOR = "___,__," // 검색어에 없을 만한 특수 구분자

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun addSearchTerm(context: Context, term: String) {
        if (term.isBlank()) return

        val prefs = getSharedPreferences(context)
        val history = getSearchHistory(context).toMutableList()

        // 기존에 있던 검색어는 제거 (맨 앞으로 이동시키기 위함)
        history.remove(term)
        // 가장 최근 검색어이므로 맨 앞에 추가
        history.add(0, term)

        // 최대 저장 개수 유지
        val trimmedHistory = if (history.size > MAX_HISTORY_SIZE) {
            history.subList(0, MAX_HISTORY_SIZE)
        } else {
            history
        }

        // 리스트를 하나의 문자열로 변환하여 저장
        val historyString = trimmedHistory.joinToString(SEPARATOR)
        prefs.edit().putString(KEY_SEARCH_HISTORY, historyString).apply()
    }

    fun getSearchHistory(context: Context): List<String> {
        val prefs = getSharedPreferences(context)
        val historyString = prefs.getString(KEY_SEARCH_HISTORY, null)

        return if (historyString.isNullOrEmpty()) {
            emptyList()
        } else {
            // 저장된 문자열을 구분자로 분리하여 리스트로 변환
            historyString.split(SEPARATOR)
        }
    }

    fun removeSearchTerm(context: Context, term: String) {
        val prefs = getSharedPreferences(context)
        val history = getSearchHistory(context).toMutableList()
        history.remove(term)

        val historyString = history.joinToString(SEPARATOR)
        prefs.edit().putString(KEY_SEARCH_HISTORY, historyString).apply()
    }
}