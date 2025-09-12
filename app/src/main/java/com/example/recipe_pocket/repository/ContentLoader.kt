package com.example.recipe_pocket.repository

import com.example.recipe_pocket.data.Quiz
import com.example.recipe_pocket.data.SeasonalIngredient
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Calendar

object ContentLoader {

    private val db = Firebase.firestore

    // 현재 달에 맞는 제철 재료를 불러옴
    suspend fun loadSeasonalIngredients(): Result<List<SeasonalIngredient>> {
        return try {
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1~12월
            val snapshot = db.collection("seasonal_ingredients")
                .whereArrayContains("months", currentMonth)
                .get()
                .await()

            val ingredients = snapshot.toObjects(SeasonalIngredient::class.java)
            Result.success(ingredients)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 퀴즈 1개를 랜덤으로 불러옴
    suspend fun loadRandomQuiz(): Result<Quiz?> {
        return try {
            val snapshot = db.collection("quizzes").get().await()
            if (snapshot.isEmpty) return Result.success(null)

            val quizzes = snapshot.toObjects(Quiz::class.java)
            Result.success(quizzes.random())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}