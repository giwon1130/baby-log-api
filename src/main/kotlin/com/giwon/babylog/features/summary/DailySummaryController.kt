package com.giwon.babylog.features.summary

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 운영/디버그용. 평소엔 스케줄러가 매일 21:00 KST 에 모든 가족에 발송.
 */
@RestController
@RequestMapping("/api/v1/summary")
class DailySummaryController(private val service: DailySummaryService) {

    /** 모든 가족에 즉시 발송. 처리된 가족 수 반환. */
    @PostMapping("/run-all")
    fun runAll(): ApiResponse<Int> = ApiResponse.ok(service.runForAllFamilies())

    /** 특정 가족에 즉시 발송. true=보냄, false=skip(빈 통계/토큰 없음 등). */
    @PostMapping("/run/{familyId}")
    fun runOne(@PathVariable familyId: String): ApiResponse<Boolean> =
        ApiResponse.ok(service.runForFamily(familyId))
}
