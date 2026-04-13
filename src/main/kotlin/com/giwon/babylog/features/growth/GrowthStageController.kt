package com.giwon.babylog.features.growth

import com.giwon.babylog.common.ApiResponse
import com.giwon.babylog.features.baby.BabyService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/babies/{babyId}/growth-stage")
class GrowthStageController(
    private val growthStageService: GrowthStageService,
    private val babyService: BabyService,
) {

    @GetMapping
    fun getGrowthStage(
        @PathVariable babyId: String,
        @RequestParam familyId: String,
    ): ApiResponse<GrowthStageResponse> {
        val baby = babyService.getBaby(familyId, babyId)
        return ApiResponse.ok(growthStageService.getStage(baby.daysOld))
    }
}
