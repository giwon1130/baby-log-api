package com.giwon.babylog.features.cry

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class CryAnalysisController(private val service: CryAnalysisService) {

    /**
     * Submit a new cry sample extracted on-device.
     * POST /api/v1/babies/{babyId}/cry-samples
     * Returns predictions; the top label is persisted, others are for UI display only.
     */
    @PostMapping("/babies/{babyId}/cry-samples")
    fun submit(
        @PathVariable babyId: String,
        @RequestBody request: SubmitCryRequest,
    ): ApiResponse<CrySampleResponse> = ApiResponse.ok(service.submit(babyId, request))

    /**
     * User confirms (or corrects) the label for a sample.
     * PATCH /api/v1/cry-samples/{id}/confirm
     * This is the learning signal — predictions improve as more samples get confirmed.
     */
    @PatchMapping("/cry-samples/{id}/confirm")
    fun confirm(
        @PathVariable id: String,
        @RequestBody request: ConfirmCryRequest,
    ): ApiResponse<CrySampleResponse> = ApiResponse.ok(service.confirm(id, request))

    @GetMapping("/babies/{babyId}/cry-samples")
    fun history(
        @PathVariable babyId: String,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ApiResponse<List<CrySampleResponse>> = ApiResponse.ok(service.history(babyId, limit))
}
