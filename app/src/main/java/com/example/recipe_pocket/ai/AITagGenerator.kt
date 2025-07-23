package com.example.recipe_pocket.ai

import com.example.recipe_pocket.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.logging.Logger // Logger 임포트 추가

object AITagGenerator {

    private val logger: Logger = Logger.getLogger(AITagGenerator::class.java.name)

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val service: GeminiApiService by lazy {
        retrofit.create(GeminiApiService::class.java)
    }

    suspend fun generateTags(recipeContent: String): Result<Set<String>> {
        return try {
            val prompt = createPromptForTagging(recipeContent)
            val request = GeminiRequest(listOf(Content(listOf(Part(prompt)))))
            val apiKey = BuildConfig.GEMINI_API_KEY
            val response = service.generateContent(apiKey, request)

            logger.info("AI 응답 전체: $response")

            if (response.candidates.isNullOrEmpty()) {
                val blockReason = response.promptFeedback?.blockReason ?: "응답 없음"
                return Result.failure(Exception("AI 응답이 비어있거나 차단되었습니다: $blockReason"))
            }

            val tags = parseTagsFromResponse(response)
            Result.success(tags)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createPromptForTagging(content: String): String {
        return """
            [역할]
            당신은 사용자의 상황과 요구(craving)에 맞는 레시피 태그를 생성하는 '맞춤 레시피 태그 전문가'입니다.
            
            [작업 목표]
            아래 [분석할 레시피 정보]를 읽고, 각 카테고리별로 **최소 하나 이상씩** 어울리는 태그를 [제공되는 태그 목록] 안에서 선택해 주세요.
            
            [제공되는 태그 목록]
            -   **온도**: 매우더운날, 더운날, 쾌적한날, 추운날, 매우추운날
            -   **날씨 현상**: 맑은날, 흐린날, 비오는날, 눈오는날
            -   **미세먼지**: 미세먼지좋음, 미세먼지보통, 미세먼지나쁨, 미세먼지매우나쁨
            -   **습도**: 습도높음, 습도낮음
            
            [핵심 규칙 및 사고 과정]
            1.  **태그 제한**: 반드시 [제공되는 태그 목록]에 있는 단어만 사용해야 합니다. 절대 다른 단어를 생성하면 안 됩니다.
            2.  **필수 카테고리 태그**: '온도', '날씨 현상', '미세먼지', '습도' 네 가지 카테고리 각각에 대해 **반드시 하나 이상의 태그를 선택해야 합니다.** 만약 특정 카테고리에서 명확하게 어울리는 태그가 없다면, 그 음식과 가장 연관성이 높거나 가장 중립적인 태그를 하나 선택하세요.
            3.  **독립적 평가 및 중복 선택**: 각 카테고리는 서로 독립적으로 평가하며, 한 카테고리 내에서도 여러 태그가 동시에 적용될 수 있습니다.
                -   (예시 1) **제육볶음**: 날씨의 영향을 크게 받지 않는 음식이므로, '날씨 현상' 카테고리의 `[맑은날, 흐린날, 비오는날, 눈오는날]` 태그들이 모두 적용될 수 있습니다.
            4.  **'쾌적한날' 태그 활용**: '쾌적한날'은 덥지도 춥지도 않은, 야외 활동하기 좋은 날씨를 의미합니다. 이 날씨에는 피크닉, 가벼운 나들이 등에 어울리는 음식(예: 샌드위치, 샐러드, 도시락)을 추천하는 데 해당 태그를 사용합니다.
            5.  **복합적, 문화적 추론**: 단순 사실 관계를 넘어, 음식의 특성과 상황 간의 복합적인 연관성을 추론해야 합니다.
                -   (예시 2) **김치찌개**: '미세먼지나쁨' 또는 '미세먼지매우나쁨'일 경우, 목의 칼칼함을 해소하기 위해 칼칼한 국물 요리가 생각날 수 있으므로 해당 태그들을 추가합니다.
                -   (예시 3) **삼계탕**: 뜨거운 보양식이지만, 한국의 '이열치열' 문화에 따라 '매우더운날', '더운날'에도 즐겨 찾습니다. 따라서 삼계탕 레시피에는 해당 태그들을 포함해야 합니다. (이는 주로 국물류 음식에 한합니다.)
            6.  **출력 형식**: 최종 결과는 다른 설명 없이, 쉼표(,)로만 구분된 태그 목록 문자열로만 응답해야 합니다. (예: "더운날,맑은날,미세먼지보통,습도높음")
            
            7. 레시피와 일치하는 태그를 모두 적어야됩니다. 예 : 아이스크림 (매우더운날,더운날,맑은날,미세먼지좋음,미세먼지보통,미세먼지나쁨,미세먼지매우나쁨,습도높음)
            8. 온도는 모두 일치해도 최소 한가지의 태그는 없어야 합니다. 예로 닭강정은 언제나 먹어도 상관없지만, 뜨거운 음식이기에 매우더운날 태그는 제거해야합니다. (아니면 더운날도 같이 제거해도 됩니다.)
            
            
            --- 
            [분석할 레시피 정보]
            $content
            
            [결과]
        """.trimIndent()
    }

    private fun parseTagsFromResponse(response: GeminiResponse): Set<String> {
        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        return textResponse
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}