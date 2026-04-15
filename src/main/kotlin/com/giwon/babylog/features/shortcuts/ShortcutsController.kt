package com.giwon.babylog.features.shortcuts

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/shortcuts")
class ShortcutsController(private val shortcutsService: ShortcutsService) {

    // ── 기록형 ───────────────────────────────────────────────────────────────

    /** 수유 기록 (분유/모유/혼합 공통)
     *  POST /api/v1/shortcuts/feed
     *  { inviteCode, babyName?, amountMl?, feedType?, leftMinutes?, rightMinutes? } */
    @PostMapping("/feed")
    fun recordFeed(@RequestBody req: ShortcutFeedRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.recordFeed(req))

    /** 기저귀 기록
     *  POST /api/v1/shortcuts/diaper
     *  { inviteCode, babyName?, diaperType? } */
    @PostMapping("/diaper")
    fun recordDiaper(@RequestBody req: ShortcutDiaperRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.recordDiaper(req))

    /** 수면 시작
     *  POST /api/v1/shortcuts/sleep/start
     *  { inviteCode, babyName? } */
    @PostMapping("/sleep/start")
    fun startSleep(@RequestBody req: ShortcutSleepRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.startSleep(req))

    /** 수면 종료
     *  POST /api/v1/shortcuts/sleep/end
     *  { inviteCode, babyName? } */
    @PostMapping("/sleep/end")
    fun endSleep(@RequestBody req: ShortcutSleepRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.endSleep(req))

    /** 체온 기록
     *  POST /api/v1/shortcuts/health/temperature
     *  { inviteCode, babyName?, temperature, note? } */
    @PostMapping("/health/temperature")
    fun recordTemperature(@RequestBody req: ShortcutTemperatureRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.recordTemperature(req))

    /** 약 투여 기록
     *  POST /api/v1/shortcuts/health/medicine
     *  { inviteCode, babyName?, medicineName, dosage?, note? } */
    @PostMapping("/health/medicine")
    fun recordMedicine(@RequestBody req: ShortcutMedicineRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.recordMedicine(req))

    /** 성장 기록
     *  POST /api/v1/shortcuts/growth
     *  { inviteCode, babyName?, weightG?, heightCm?, headCm?, note? } */
    @PostMapping("/growth")
    fun recordGrowth(@RequestBody req: ShortcutGrowthRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.recordGrowth(req))

    // ── 조회형 ───────────────────────────────────────────────────────────────

    /** 현재 상태 (최근 수유 + 수면 중 여부 + 오늘 요약)
     *  POST /api/v1/shortcuts/status
     *  { inviteCode, babyName? } */
    @PostMapping("/status")
    fun getStatus(@RequestBody req: ShortcutStatusRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.getStatus(req))

    /** 오늘 요약
     *  POST /api/v1/shortcuts/today
     *  { inviteCode, babyName? } */
    @PostMapping("/today")
    fun getTodaySummary(@RequestBody req: ShortcutStatusRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.getTodaySummary(req))
}
