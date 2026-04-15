package com.giwon.babylog.features.shortcuts

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/shortcuts")
class ShortcutsController(private val shortcutsService: ShortcutsService) {

    /** 수유 기록: POST /api/v1/shortcuts/feed */
    @PostMapping("/feed")
    fun recordFeed(@RequestBody req: ShortcutFeedRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.recordFeed(req))

    /** 기저귀 기록: POST /api/v1/shortcuts/diaper */
    @PostMapping("/diaper")
    fun recordDiaper(@RequestBody req: ShortcutDiaperRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.recordDiaper(req))

    /** 수면 시작: POST /api/v1/shortcuts/sleep/start */
    @PostMapping("/sleep/start")
    fun startSleep(@RequestBody req: ShortcutSleepRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.startSleep(req))

    /** 수면 종료: POST /api/v1/shortcuts/sleep/end */
    @PostMapping("/sleep/end")
    fun endSleep(@RequestBody req: ShortcutSleepRequest): ApiResponse<ShortcutResult> =
        ApiResponse.ok(shortcutsService.endSleep(req))
}
