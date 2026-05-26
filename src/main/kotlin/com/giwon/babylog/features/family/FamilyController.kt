package com.giwon.babylog.features.family

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/families")
class FamilyController(private val familyService: FamilyService) {

    @PostMapping
    fun createFamily(): ApiResponse<FamilyResponse> =
        ApiResponse.ok(familyService.createFamily())

    @GetMapping("/{familyId}")
    fun getFamily(@PathVariable familyId: String): ApiResponse<FamilyResponse> =
        ApiResponse.ok(familyService.getFamily(familyId))

    @GetMapping("/join/{inviteCode}")
    fun joinFamily(@PathVariable inviteCode: String): ApiResponse<FamilyResponse> =
        ApiResponse.ok(familyService.joinFamily(inviteCode))

    /**
     * 빈 가족(아기 0명) 삭제. 아기가 남아 있으면 409 IllegalStateException 으로 거부.
     */
    @DeleteMapping("/{familyId}")
    fun deleteFamily(@PathVariable familyId: String): ApiResponse<Map<String, String>> {
        familyService.deleteEmptyFamily(familyId)
        return ApiResponse.ok(mapOf("status" to "deleted", "familyId" to familyId))
    }

    /**
     * 빈 가족 일괄 정리. 운영 청소용 — 졸업 후 남은 빈 가족 row 와 그 푸시 토큰을 한 번에 제거.
     */
    @PostMapping("/cleanup-empty")
    fun cleanupEmptyFamilies(): ApiResponse<Map<String, Int>> {
        val deleted = familyService.cleanupEmptyFamilies()
        return ApiResponse.ok(mapOf("deleted" to deleted))
    }
}
