package com.giwon.babylog.bootstrap

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 단순 헬스체크. keep-alive ping / 외부 모니터링 대상.
 * ApiResponse 래퍼 없이 raw 응답 — 모니터링 도구 호환성 위해.
 */
@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf(
        "status" to "UP",
        "application" to "baby-log-api",
    )
}
