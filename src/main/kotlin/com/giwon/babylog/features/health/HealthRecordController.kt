package com.giwon.babylog.features.health

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/babies/{babyId}/health-records")
class HealthRecordController(private val healthRecordService: HealthRecordService) {

    @PostMapping
    fun recordHealth(
        @PathVariable babyId: String,
        @RequestBody request: CreateHealthRecordRequest,
    ): ApiResponse<HealthRecordResponse> = ApiResponse.ok(healthRecordService.recordHealth(babyId, request))

    @GetMapping
    fun getHealthRecords(
        @PathVariable babyId: String,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ApiResponse<List<HealthRecordResponse>> =
        ApiResponse.ok(healthRecordService.getHealthRecords(babyId, limit))

    @DeleteMapping("/{recordId}")
    fun deleteHealthRecord(
        @PathVariable babyId: String,
        @PathVariable recordId: String,
    ): ApiResponse<Unit> {
        healthRecordService.deleteHealthRecord(babyId, recordId)
        return ApiResponse.ok(Unit)
    }
}
