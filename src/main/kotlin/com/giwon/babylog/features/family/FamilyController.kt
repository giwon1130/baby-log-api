package com.giwon.babylog.features.family

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/families")
class FamilyController(private val familyService: FamilyService) {

    @PostMapping
    fun createFamily(): ApiResponse<FamilyResponse> =
        ApiResponse.ok(familyService.createFamily())

    @GetMapping("/join/{inviteCode}")
    fun joinFamily(@PathVariable inviteCode: String): ApiResponse<FamilyResponse> =
        ApiResponse.ok(familyService.joinFamily(inviteCode))
}
