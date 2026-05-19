package com.giwon.babylog.features.summary

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.giwon.babylog.features.stats.TodayStatsResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Gemini API 단일 호출 — 일일 요약을 자연어로 풍부하게.
 *
 * - 무료 한도: gemini-2.0-flash-exp 15 req/분, 1500 req/일.
 *   하루 1회/가족 발송 → 가족 수가 1500 미만이면 무료.
 * - api-key 미설정 시 isEnabled() = false → caller 가 fallback 사용.
 */
@Component
class GeminiClient(
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

    /**
     * 일일 통계를 한국어 자연어 요약(2~3문장)으로 변환.
     * 실패 시 null — caller 가 template fallback.
     */
    fun summarizeDaily(babyName: String, stats: TodayStatsResponse): String? {
        if (!isEnabled()) return null
        val prompt = buildPrompt(babyName, stats)

        return runCatching {
            val url = "$baseUrl/v1beta/models/$model:generateContent?key=$apiKey"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(prompt)))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.warn("Gemini status={} body={}", response.statusCode(), response.body().take(300))
                return@runCatching null
            }
            parseText(response.body())
        }.getOrElse {
            log.warn("Gemini call failed", it)
            null
        }
    }

    private fun buildPrompt(babyName: String, s: TodayStatsResponse): String {
        val sleepH = s.totalSleepMinutes / 60
        val sleepM = s.totalSleepMinutes % 60
        val avgInterval = s.avgFeedIntervalMinutes?.let { "${it.toInt()}분" } ?: "데이터 부족"

        return """
            당신은 신생아 부모를 응원하는 따뜻한 톤의 보조 도우미입니다.
            아래는 오늘 ${babyName}의 활동 통계입니다. 부모가 한 눈에 흐름을 파악하고
            기분 좋게 받을 수 있도록, **2~3문장**으로 한국어 자연어 요약을 작성하세요.

            지침:
            - 부담스럽지 않은, 부드럽고 응원하는 톤
            - 수치는 자연스럽게 풀어서 (예: "여덟 번", "720밀리리터" 보단 숫자+단위)
            - 평가나 비교(많다/적다)는 하지 말 것. 그날 흐름만 객관적으로
            - 마지막에 가벼운 응원 한 줄 (예: "오늘도 수고했어요" 정도)
            - 이모지는 1~2개만, 과하지 않게
            - 출력은 본문 텍스트만 (JSON·따옴표·markdown 헤더 X)

            오늘 통계:
            - 수유: ${s.feedCount}회, 총 ${s.totalFeedMl}ml
            - 평균 수유 간격: $avgInterval
            - 기저귀: ${s.diaperCount}회 (소변 ${s.wetCount} · 대변 ${s.dirtyCount})
            - 수면: ${s.sleepCount}회, 총 ${sleepH}시간 ${sleepM}분
            - 최장 연속 수면: ${s.longestSleepMinutes}분
        """.trimIndent()
    }

    private fun buildPayload(prompt: String): String {
        val root = objectMapper.createObjectNode()
        val contents = root.putArray("contents")
        val msg = contents.addObject()
        val parts = msg.putArray("parts")
        parts.addObject().put("text", prompt)

        val gen = root.putObject("generationConfig")
        gen.put("temperature", 0.7)
        gen.put("maxOutputTokens", 512)
        // thinking 모드 끄기 — 단일 텍스트 응답 강제
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
