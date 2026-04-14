package com.giwon.babylog.features.stats

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/babies/{babyId}/stats")
class StatsController(private val statsService: StatsService) {

    @GetMapping("/today")
    fun getTodayStats(@PathVariable babyId: String): ApiResponse<TodayStatsResponse> =
        ApiResponse.ok(statsService.getTodayStats(babyId))
}
