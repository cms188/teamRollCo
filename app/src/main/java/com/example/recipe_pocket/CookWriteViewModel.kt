package com.example.recipe_pocket

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CookWriteViewModel : ViewModel() {

    private val _recipeData = MutableLiveData<RecipeData>()
    val recipeData: LiveData<RecipeData> = _recipeData

    private val _steps = MutableLiveData<MutableList<RecipeStep_write>>()
    val steps: LiveData<MutableList<RecipeStep_write>> = _steps

    init {
        // ViewModel이 처음 생성될 때, 첫 번째 단계를 가진 데이터로 초기화
        val initialSteps = mutableListOf(RecipeStep_write())
        _recipeData.value = RecipeData(steps = initialSteps)
        _steps.value = initialSteps
    }

    // CookWrite01, 02에서 받아온 기본 데이터를 설정하는 함수
    fun setInitialRecipeData(initialData: RecipeData) {
        val currentData = _recipeData.value ?: RecipeData()
        currentData.thumbnailUrl = initialData.thumbnailUrl
        currentData.category = initialData.category
        currentData.title = initialData.title
        currentData.description = initialData.description
        currentData.difficulty = initialData.difficulty
        currentData.servings = initialData.servings
        currentData.cookingTimeMinutes = initialData.cookingTimeMinutes
        currentData.ingredients = initialData.ingredients
        currentData.tools = initialData.tools
        // steps는 이미 초기화되었으므로 그대로 둠
        _recipeData.postValue(currentData)
    }

    // 단계 추가
    fun addStep() {
        val currentSteps = _steps.value ?: mutableListOf()
        currentSteps.add(RecipeStep_write())
        _steps.value = currentSteps
    }

    // 단계 삭제
    fun removeStepAt(position: Int) {
        val currentSteps = _steps.value ?: return
        if (currentSteps.size > 1 && position >= 0 && position < currentSteps.size) {
            currentSteps.removeAt(position)
            // LiveData에 변경된 리스트를 다시 할당하여 옵저버에게 알림
            _steps.value = currentSteps
        }
    }
}