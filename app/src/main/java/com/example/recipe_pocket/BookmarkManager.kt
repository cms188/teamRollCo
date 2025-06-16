package com.example.recipe_pocket

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

object BookmarkManager {

    private val db = Firebase.firestore

    fun toggleBookmark(recipeId: String, userId: String, isCurrentlyBookmarked: Boolean, onComplete: (Result<Boolean>) -> Unit) {
        val recipeRef = db.collection("Recipes").document(recipeId)

        val updateTask = if (isCurrentlyBookmarked) {
            recipeRef.update("bookmarkedBy", FieldValue.arrayRemove(userId))
        } else {
            recipeRef.update("bookmarkedBy", FieldValue.arrayUnion(userId))
        }

        updateTask
            .addOnSuccessListener {
                onComplete(Result.success(!isCurrentlyBookmarked))
            }
            .addOnFailureListener { e ->
                onComplete(Result.failure(e))
            }
    }
}