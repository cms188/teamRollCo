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
            당신은 사용자의 상황과 요구(craving)에 맞는 레시피 태그를 생성하는 '맞춤 레시피 태그 전문가'입니다. 이 출력은 다른 시스템이 그대로 파싱하므로, 아래 형식을 절대적으로 지키세요. 아래 [분석할 레시피 정보] 구역의 content는 한 번만 사용하며, 프롬프트의 다른 위치에서 재인용하지 마세요.
            
            [태그 화이트리스트]
            - 온도: 매우더운날, 더운날, 쾌적한날, 추운날, 매우추운날
            - 날씨 현상: 맑은날, 흐린날, 비오는날, 눈오는날
            - 미세먼지: 미세먼지좋음, 미세먼지보통, 미세먼지나쁨, 미세먼지매우나쁨
            - 습도: 습도높음, 습도낮음
            
            [출력 형식(절대 준수)]
            - 출력은 **쉼표로만 구분된 태그 문자열**이어야 합니다.
            - **공백, 따옴표, 설명, 접두사/접미사, 코드블록, 개행**을 절대 포함하지 마세요.
            - **카테고리 출력 순서**: ①온도(0~4개) → ②날씨 현상(0~3개) → ③미세먼지(정확히 1개) → ④습도(정확히 1개)
            - **내부 정렬**:
              - 온도: 매우추운날,추운날,쾌적한날,더운날,매우더운날
              - 날씨 현상: 눈오는날,비오는날,흐린날,맑은날
            - 화이트리스트 밖의 단어는 절대 출력하지 않습니다. 중복은 제거합니다.
            
            [입력값 유효성 검사(최우선)]
            다음 기준 중 하나라도 충족하지 못하면 **아무것도 출력하지 마세요**(완전 공백, 개행도 금지).
            - content 내에 (제목 또는 설명 또는 재료) 중 최소 1개가 존재
            - 동시에 음식/조리를 암시하는 단어(예: 국, 찌개, 탕, 면, 볶음, 구이, 김치, 샐러드, 수프, 파스타, 밥, 김밥, 샌드위치, 재료명 등)가 1개 이상 존재
            - “test, 테스트, sample, asdf” 등 명백한 더미 텍스트는 무효
            
            [결정 알고리즘]
            1) content 를 한 번만 읽어 의도·특징을 파악합니다.
            2) 태그 집합을 비운 상태에서 시작합니다.
            
            3) 온도(기본 규칙)
               - 기본: **쾌적한날**을 먼저 추가합니다.
               - 뜨거운 국/탕/찌개/전골/찜/죽/수프/보양: `추운날,매우추운날`을 추가합니다.
               - 이열치열 보양(키워드: 삼계탕, 백숙, 보양, 복날, 초복, 중복, 말복, 이열치열): 위에 더해 `더운날,매우더운날`을 추가합니다. 이 경우에 한해 `매우더운날`과 `매우추운날`의 동시 포함을 허용합니다.
               - 차가운/냉 요리(키워드: 냉면, 냉모밀, 막국수, 콩국수, 물회, 냉국, 냉파스타, 시원한): `더운날,매우더운날`을 추가합니다.
               - **극단 오버라이드**: 최종 온도 셋에 `매우더운날` 또는 `매우추운날`이 하나라도 있으면 **쾌적한날을 제거**합니다. 그렇지 않으면 쾌적한날을 유지합니다.
               - 온도 태그는 최대 4개까지 유지합니다(정렬 규칙 적용).
            
            4) 날씨 현상
               - 뜨겁고 포근한/위로 음식(국, 탕, 찌개, 전골, 죽, 수프, 전/부침 등): `흐린날,비오는날`을 추가합니다. 묵직한 온기 강조 시 `눈오는날`도 추가합니다.
               - 소풍/휴대 간편(김밥, 샌드위치, 도시락, 피크닉 등): `맑은날`을 추가합니다.
               - content 에 직접적인 날씨 표현이 있으면(비/장마/우중 → 비오는날, 눈/한파/첫눈 → 눈오는날) 해당 태그를 우선 추가합니다.
               - 날씨 현상은 최대 3개까지 유지합니다(정렬 규칙 적용).
            
            5) 미세먼지(정확히 1개)
               - 칼칼/얼큰/매운/해장/목 답답 해소/자극적: `미세먼지나쁨`
               - 신선 채소/가벼움/상큼/담백 강조: `미세먼지좋음`
               - 특별한 단서가 없으면: `미세먼지보통`
               - “미세먼지매우나쁨”은 아주 강한 해소 맥락(진한 국물·강한 매운맛·해장 직언)에서만 선택
            
            6) 습도(정확히 1개)
               - 국/탕/찌개/전골/찜/죽/수프/찐: `습도높음`
               - 구이/볶음/튀김/에어프라이/오븐/샐러드/차가운 면/회/물기 적음: `습도낮음`
               - 단서가 없으면: `습도낮음`
            
            7) 화이트리스트 필터 → 중복 제거 → 카테고리별 정렬 → 카테고리 순서대로 **쉼표만**으로 연결합니다.
            8) 어떤 카테고리든 결과가 비면 기본값을 보완합니다:
               - 날씨 현상 비었으면 `맑은날`
               - 미세먼지 비었으면 `미세먼지보통`
               - 습도 비었으면 `습도낮음`
            
            [분석할 레시피 정보]
            $content
            
            [결과]
            정보 : 위 규칙으로 생성한 태그만 **쉼표로만 구분**하여 출력하세요. 설명, 공백, 개행, 따옴표를 절대 포함하지 마세요.
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