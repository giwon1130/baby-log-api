package com.giwon.babylog.features.healthtips

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

data class AskHealthRequest(val question: String, val babyAgeMonths: Int? = null)
data class AskHealthResponse(val answer: String, val source: String)  // source = 'gemini' | 'fallback'

@RestController
@RequestMapping("/api/v1/health-tips")
class HealthTipsController(
    private val gemini: HealthGeminiClient,
) {

    /** 사전 작성된 정적 건강 가이드 카드 리스트 */
    @GetMapping
    fun list(): ApiResponse<List<HealthTip>> =
        ApiResponse.ok(HealthTipsCatalog.all)

    /** 단건 상세 — 카탈로그에 있는 ID 그대로 */
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ApiResponse<HealthTip> {
        val tip = HealthTipsCatalog.byId(id)
            ?: throw IllegalArgumentException("해당 가이드를 찾을 수 없어.")
        return ApiResponse.ok(tip)
    }

    /**
     * 사용자 자유 질문에 Gemini 답변.
     * 면책·응급 안내·일반론 톤은 시스템 프롬프트에서 강제.
     */
    @PostMapping("/ask")
    fun ask(@RequestBody req: AskHealthRequest): ApiResponse<AskHealthResponse> {
        require(req.question.isNotBlank()) { "질문이 비어 있어." }
        val answer = gemini.answer(req.question, req.babyAgeMonths)
            ?: return ApiResponse.ok(
                AskHealthResponse(
                    answer = "지금 AI 답변을 가져오지 못했어요. 잠시 후 다시 시도하거나, 위의 가이드 카드에서 비슷한 항목을 찾아보세요. 응급 상황 같으면 즉시 소아과/응급실로 가세요.",
                    source = "fallback",
                ),
            )
        return ApiResponse.ok(AskHealthResponse(answer = answer, source = "gemini"))
    }
}
