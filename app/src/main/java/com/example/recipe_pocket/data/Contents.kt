package com.example.recipe_pocket.data

data class SeasonalIngredient(
    val name: String? = null,
    val imageUrl: String? = null,
    val months: List<Int>? = null
) {
    constructor() : this(null, null, null)
}

data class Quiz(
    val question: String? = null,
    val answer: Boolean? = null,
    val explanation: String? = null
) {
    constructor() : this(null, null, null)
}