package com.giwon.babylog.features.monthlyphoto

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/babies/{babyId}/monthly-photos")
class MonthlyPhotoController(private val service: MonthlyPhotoService) {

    @GetMapping
    fun list(@PathVariable babyId: String): ApiResponse<List<MonthlyPhotoResponse>> =
        ApiResponse.ok(service.list(babyId))

    /** 슬롯에 사진 저장(또는 교체). Cloudinary 직업로드 결과를 받아 백엔드는 URL/메타만 보관. */
    @PostMapping
    fun upsert(
        @PathVariable babyId: String,
        @RequestBody req: MonthlyPhotoUpsertRequest,
    ): ApiResponse<MonthlyPhotoResponse> =
        ApiResponse.ok(service.upsert(babyId, req))

    @DeleteMapping("/{monthIndex}")
    fun delete(
        @PathVariable babyId: String,
        @PathVariable monthIndex: Int,
    ): ApiResponse<Map<String, Any>> {
        service.delete(babyId, monthIndex)
        return ApiResponse.ok(mapOf("status" to "deleted", "monthIndex" to monthIndex))
    }
}
