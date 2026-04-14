package com.giwon.babylog.features.sleep

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/babies/{babyId}/sleeps")
class SleepController(private val sleepService: SleepService) {

    @PostMapping("/start")
    fun startSleep(
        @PathVariable babyId: String,
        @RequestBody request: StartSleepRequest,
    ): ApiResponse<SleepResponse> = ApiResponse.ok(sleepService.startSleep(babyId, request))

    @PostMapping("/{sleepId}/end")
    fun endSleep(
        @PathVariable babyId: String,
        @PathVariable sleepId: String,
        @RequestBody request: EndSleepRequest,
    ): ApiResponse<SleepResponse> = ApiResponse.ok(sleepService.endSleep(babyId, sleepId, request))

    @GetMapping
    fun getSleepRecords(
        @PathVariable babyId: String,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<SleepResponse>> =
        ApiResponse.ok(sleepService.getSleepRecords(babyId, limit))

    @GetMapping("/active")
    fun getActiveSleep(@PathVariable babyId: String): ApiResponse<SleepResponse?> =
        ApiResponse.ok(sleepService.getActiveSleep(babyId))

    @PutMapping("/{sleepId}")
    fun updateSleep(
        @PathVariable babyId: String,
        @PathVariable sleepId: String,
        @RequestBody request: UpdateSleepRequest,
    ): ApiResponse<SleepResponse> = ApiResponse.ok(sleepService.updateSleep(babyId, sleepId, request))

    @DeleteMapping("/{sleepId}")
    fun deleteSleep(
        @PathVariable babyId: String,
        @PathVariable sleepId: String,
    ): ApiResponse<Unit> {
        sleepService.deleteSleep(babyId, sleepId)
        return ApiResponse.ok(Unit)
    }
}
