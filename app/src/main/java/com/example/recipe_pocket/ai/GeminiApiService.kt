package com.example.recipe_pocket.ai

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

// API 요청/응답에 사용할 데이터 클래스
data class GeminiRequest(val contents: List<Content>)
data class Content(val parts: List<Part>)
data class Part(val text: String)

data class GeminiResponse(
    val candidates: List<Candidate>?,
    val promptFeedback: PromptFeedback?
)
data class Candidate(val content: Content)
data class PromptFeedback(val blockReason: String?)

// Retrofit 서비스 인터페이스
interface GeminiApiService {
    @POST("v1beta/models/gemini-2.5-flash-lite-preview-06-17:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}