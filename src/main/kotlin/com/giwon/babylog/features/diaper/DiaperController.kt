package com.giwon.babylog.features.diaper

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/babies/{babyId}/diapers")
class DiaperController(private val diaperService: DiaperService) {

    @PostMapping
    fun recordDiaper(
        @PathVariable babyId: String,
        @RequestBody request: CreateDiaperRequest,
    ): ApiResponse<DiaperResponse> = ApiResponse.ok(diaperService.recordDiaper(babyId, request))

    @GetMapping
    fun getDiapers(
        @PathVariable babyId: String,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<DiaperResponse>> = ApiResponse.ok(diaperService.getDiapers(babyId, limit))

    @GetMapping("/latest")
    fun getLatestDiaper(@PathVariable babyId: String): ApiResponse<DiaperResponse?> =
        ApiResponse.ok(diaperService.getLatestDiaper(babyId))
}
