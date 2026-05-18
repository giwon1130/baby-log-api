package com.giwon.babylog.features.push

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/families/{familyId}/push-tokens")
class PushTokenController(private val service: PushTokenService) {

    @PostMapping
    fun register(
        @PathVariable familyId: String,
        @RequestBody request: RegisterPushTokenRequest,
    ): ApiResponse<PushTokenResponse> = ApiResponse.ok(service.register(familyId, request))

    @DeleteMapping("/{deviceId}")
    fun delete(
        @PathVariable familyId: String,
        @PathVariable deviceId: String,
    ): ApiResponse<Unit> {
        service.delete(familyId, deviceId)
        return ApiResponse.ok(Unit)
    }
}
