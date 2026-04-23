package com.giwon.babylog.features.baby

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/families/{familyId}/babies")
class BabyController(private val babyService: BabyService) {

    @PostMapping
    fun createBaby(
        @PathVariable familyId: String,
        @RequestBody request: CreateBabyRequest,
    ): ApiResponse<BabyResponse> = ApiResponse.ok(babyService.createBaby(familyId, request))

    @GetMapping
    fun getBabies(@PathVariable familyId: String): ApiResponse<List<BabyResponse>> =
        ApiResponse.ok(babyService.getBabies(familyId))

    @GetMapping("/{babyId}")
    fun getBaby(
        @PathVariable familyId: String,
        @PathVariable babyId: String,
    ): ApiResponse<BabyResponse> = ApiResponse.ok(babyService.getBaby(familyId, babyId))

    @PutMapping("/{babyId}")
    fun updateBaby(
        @PathVariable familyId: String,
        @PathVariable babyId: String,
        @RequestBody request: UpdateBabyRequest,
    ): ApiResponse<BabyResponse> = ApiResponse.ok(babyService.updateBaby(familyId, babyId, request))

    @DeleteMapping("/{babyId}")
    fun deleteBaby(
        @PathVariable familyId: String,
        @PathVariable babyId: String,
    ): ApiResponse<Map<String, Any>> {
        babyService.deleteBaby(familyId, babyId)
        return ApiResponse.ok(mapOf("deleted" to true, "babyId" to babyId))
    }
}
