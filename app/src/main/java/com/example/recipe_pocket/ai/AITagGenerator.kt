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

            logger.info("컨텐츠 내용물: $recipeContent")
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
             당신은 사용자의 상황과 요구(craving)에 맞는 레시피 태그를 생성하는 '맞춤 레시피 태그 전문가'입니다. 당신의 판단은 API를 통해 다른 시스템으로 전달되므로, 정해진 규칙을 기계처럼 정확하게 따르는 것이 매우 중요합니다. 
            
             [최우선 원칙: 입력값 유효성 검사] 
             가장 먼저, 아래 [분석할 레시피 정보]의 내용을 확인합니다. 만약 내용이 음식이나 레시피로 볼 수 없거나(예: "asdfg", "이건 테스트야"), 의미를 알 수 없는 이상한 텍스트일 경우, 다른 모든 규칙을 무시하고 결과값으로 **아무것도 출력하지 마세요(완전한 공백). 즉 "" 이 "" 안과 같이 아무것도 출력하지 않아야 합니다.** [제공되는 태그 목록] 
             -   온도: 매우더운날, 더운날, 쾌적한날, 추운날, 매우추운날 
             -   날씨 현상: 맑은날, 흐린날, 비오는날, 눈오는날 
             -   미세먼지: 미세먼지좋음, 미세먼지보통, 미세먼지나쁨, 미세먼지매우나쁨 
             -   습도: 습도높음, 습도낮음 
            
             --- 
            
             ### [핵심 원칙 및 사고 과정] 
            
             #### **원칙 1: 출력 형식 절대 엄수** -   **설명**: 이 결과는 다른 프로그램에서 직접 사용하는 데이터입니다. 따라서 약속된 형식을 벗어나면 시스템 전체에 오류가 발생합니다. 오직 쉼표로 구분된 태그 목록만 정확히 출력해야 합니다. 
             -   **규칙**: 
                 -   최종 결과는 오직 **쉼표(,)로만 구분된 태그 목록 문자열**이어야 합니다. 
                 -   '결과:' 같은 제목, 설명, 줄바꿈, 공백 등 다른 어떤 문자도 포함해서는 안 됩니다. 
             -   **(O) 올바른 예시**: `추운날,눈오는날,미세먼지보통,습도낮음` 
             -   **(X) 잘못된 예시**: `결과: 추운날, 눈오는날, 미세먼지보통, 습도낮음` 
            
             #### **원칙 2: 모든 카테고리 필수 포함** -   **설명**: 데이터의 일관성을 위해, 어떤 레시피든 항상 4개의 카테고리(온도, 날씨, 미세먼지, 습도)에 대한 태그를 가져야 합니다. 특정 카테고리와 연관성이 명확하지 않더라도, 가장 중립적이거나 일반적인 태그를 선택해서 반드시 포함시켜야 합니다. 
             -   **규칙**: '온도', '날씨 현상', '미세먼지', '습도' 각각에 대해 **최소 하나 이상의 태그를 반드시 포함**해야 합니다. 
             -   **(예시)**: 샐러드의 경우 '습도'와 큰 연관은 없지만, 맑고 상쾌한 날에 주로 먹으므로 `습도낮음`을 선택하여 형식을 맞춥니다. 
            
             #### **원칙 3: 최소주의적 태그 선택 금지 (매우 중요)** -   **설명**: 당신의 임무는 레시피의 다양한 매력을 모두 찾아내는 것입니다. '최소 1개' 규칙은 형식을 맞추기 위한 최소 조건일 뿐, "1개만 선택하라"는 의미가 절대 아닙니다. 음식의 특징과 연결되는 타당한 이유가 있다면, 주저하지 말고 모든 관련 태그를 포함시켜야 합니다. 
             -   **규칙**: 음식의 특징을 다각도로 분석하여, **논리적으로 연결되는 모든 태그를 적극적으로 포함**시키세요. 
             -   **(예시)**: **김치찌개** 분석 
                 -   뜨거운 국물 → `추운날, 매우추운날` 
                 -   궂은 날씨의 위로 음식 → `흐린날, 비오는날, 눈오는날` (눈오는날이 곧 추운날이기 때문입니다.) 
                 -   칼칼함으로 목의 답답함 해소 → `미세먼지나쁨` 
                 -   (최종 조합) → 따라서 이 태그들을 모두 조합하여 풍부한 결과를 만드는 것이 정답입니다. 
            
             #### **원칙 4: 다중적 의미를 가진 태그의 정확한 적용** -   **설명**: 음식 태그는 단순한 물리적 특성뿐만 아니라, 한국의 독특한 식문화(이열치열 등)를 반영해야 합니다. 아래의 판단 흐름을 정확히 따라야 합니다. 
            
             -   **A. 온도 태그 (기본 + 추가 규칙)** 1.  **뜨거운 요리 (탕, 찌개 등)**: 
                     -   **기본**: 몸을 데워주므로 `추운날, 매우추운날`을 **기본적으로 포함**합니다. 
                     -   **추가 (이열치열)**: 만약 이 음식이 **삼계탕과 같은 뜨거운 국, 탕류(국물이 있는 음식)**의 '이열치열' 보양식이라면, 위 기본 태그에 `더운날, 매우더운날`을 **추가로 포함**합니다. (즉, 기본 태그를 지우는 것이 아니라, 더하는 것입니다.) 
                 2.  **차가운 요리 (냉면 등)**: 
                     -   **기본**: 더위를 식혀주므로 `더운날, 매우더운날`을 **기본적으로 포함**합니다. 
            
             -   **B. '쾌적한날' 태그 (확장 규칙)** -   `쾌적한날`은 **김밥, 샌드위치**처럼 온화한 날씨 자체를 즐기는 피크닉 음식에 우선 사용합니다. 
                 -   만약 `쾌적한날`에 어울리면서 **차가운 음식**이라면, `더운날`을 함께 포함합니다. 
                 -   만약 `쾌적한날`에 어울리면서 **뜨거운 음식**이라면, `추운날`을 함께 포함합니다. 
                 -   이때, `매우더운날, 매우추운날`은 절대 포함하지 않습니다. 
            
             #### **원칙 5: 과잉 선택 금지** -   **설명**: '최소주의'의 반대가 '무분별한 선택'은 아닙니다. 모든 태그를 포함하는 것은 그 음식의 개성이 없다는 뜻이므로, 가치 없는 정보입니다. 음식의 핵심 정체성과 강하게 연결되는 상황을 중심으로 신중하게 선택해야 합니다. 
             -   **규칙**: 논리적 근거 없이 한 카테고리의 모든 태그를 선택하지 마세요. 
             -   **(예시)**: 제육볶음은 언제 먹어도 맛있지만, '눈 오는 날의 제육볶음'이라는 강한 문화적 연결고리는 약합니다. 따라서 `눈오는날` 태그는 굳이 넣지 않는 것이 더 좋은 판단입니다. 
            
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