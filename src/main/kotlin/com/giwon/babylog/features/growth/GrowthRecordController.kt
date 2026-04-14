package com.giwon.babylog.features.growth

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/babies/{babyId}/growth-records")
class GrowthRecordController(private val growthRecordService: GrowthRecordService) {

    @PostMapping
    fun recordGrowth(
        @PathVariable babyId: String,
        @RequestBody request: CreateGrowthRecordRequest,
    ): ApiResponse<GrowthRecordResponse> =
        ApiResponse.ok(growthRecordService.recordGrowth(babyId, request))

    @GetMapping
    fun getGrowthRecords(
        @PathVariable babyId: String,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<GrowthRecordResponse>> =
        ApiResponse.ok(growthRecordService.getGrowthRecords(babyId, limit))
}
