package com.giwon.babylog.features.healthtips

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 건강 가이드 자유 질문 응답용 Gemini 호출.
 *
 * 일일 요약과 키·모델은 공유하지만 system prompt 가 다르므로 별도 클라이언트로 분리.
 * - 의학 면책 강제
 * - 진단·약 추천 X, 일반론만
 * - 응급 사인 발견 시 즉시 응급실 안내
 */
@Component
class HealthGeminiClient(
    private val objectMapper: ObjectMapper,
    @Value("\${baby-log.integrations.gemini.api-key}") private val apiKey: String,
    @Value("\${baby-log.integrations.gemini.base-url}") private val baseUrl: String,
    @Value("\${baby-log.integrations.gemini.model}") private val model: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun isEnabled(): Boolean = apiKey.isNotBlank()

    fun answer(question: String, babyAgeMonths: Int?): String? {
        if (!isEnabled()) return null
        val prompt = buildPrompt(question, babyAgeMonths)
        return runCatching {
            val url = "$baseUrl/v1beta/models/$model:generateContent?key=$apiKey"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(prompt)))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.warn("HealthGemini status={} body={}", response.statusCode(), response.body().take(300))
                return@runCatching null
            }
            parseText(response.body())
        }.getOrElse {
            log.warn("HealthGemini call failed", it)
            null
        }
    }

    private fun buildPrompt(question: String, ageMonths: Int?): String {
        val ageLine = ageMonths?.let { "- 아기 월령: ${it}개월" } ?: "- 아기 월령: 알 수 없음"
        return """
            당신은 신생아 0~12개월 부모를 돕는 한국어 건강 가이드 보조 도우미입니다.

            반드시 따라야 할 규칙:
            1. **진단·치료를 결정하지 마세요**. 일반적인 정보·관찰 포인트·일반 관리 팁만 제공.
            2. 답변 마지막에 반드시 한 줄 면책: "참고용 정보예요. 진단·치료는 소아과 상담이 필수예요."
            3. 응급 신호(고열·청색증·심한 처짐·녹색구토·경련·혈변·6시간 이상 울음·생후 3개월 미만 38°C+ 등)가 질문에 보이면 답변 첫 줄에 "🚨 지금 증상은 즉시 소아과/응급실 진료가 필요해요." 라고 안내하세요.
            4. 약 이름·용량·복용 횟수를 추천하지 마세요. "처방받은 약을 지시대로" 같은 일반론만.
            5. 단정적 표현(반드시·확실히) 피하고, "이런 경우가 흔해요", "이럴 가능성이 있어요" 처럼 부드럽게.
            6. 짧고 명확하게 — 5~8문장 이내. 핵심: 무엇일 가능성, 자가 체크 포인트, 일반 관리 팁, 병원 가야 하는 사인.
            7. 한국어 자연어. markdown 헤더 없이 본문 텍스트.
            8. 이모지는 1~2개만, 응급 신호일 때 🚨 사용.

            컨텍스트:
            $ageLine

            부모의 질문:
            $question
        """.trimIndent()
    }

    private fun buildPayload(prompt: String): String {
        val root = objectMapper.createObjectNode()
        val contents = root.putArray("contents")
        val msg = contents.addObject()
        val parts = msg.putArray("parts")
        parts.addObject().put("text", prompt)

        val gen = root.putObject("generationConfig")
        gen.put("temperature", 0.5)        // 의학 컨텐츠라 조금 더 보수적
        gen.put("maxOutputTokens", 700)
        gen.putObject("thinkingConfig").put("thinkingBudget", 0)
        return objectMapper.writeValueAsString(root)
    }

    private fun parseText(body: String): String? {
        val root = objectMapper.readTree(body)
        val candidate = root["candidates"]?.firstOrNull() ?: return null
        val parts = candidate["content"]?.get("parts") ?: return null
        val text = buildString {
            parts.forEach { part ->
                if (part["thought"]?.asBoolean() == true) return@forEach
                part["text"]?.asText()?.let { append(it) }
            }
        }.trim()
        return text.ifBlank { null }
    }
}
