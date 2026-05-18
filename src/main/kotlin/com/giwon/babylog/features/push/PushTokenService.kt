package com.giwon.babylog.features.push

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

data class PushTokenResponse(
    val deviceId: String,
    val familyId: String,
    val expoToken: String,
    val label: String,
    val platform: String,
)

data class RegisterPushTokenRequest(
    val deviceId: String,
    val expoToken: String,
    val label: String = "",
    val platform: String = "ios",
)

@Service
class PushTokenService(private val jdbc: JdbcTemplate) {

    fun register(familyId: String, request: RegisterPushTokenRequest): PushTokenResponse {
        jdbc.update(
            """
            insert into bl_push_tokens (device_id, family_id, expo_token, label, platform, updated_at)
            values (?, ?, ?, ?, ?, now())
            on conflict (device_id) do update set
                family_id = excluded.family_id,
                expo_token = excluded.expo_token,
                label = excluded.label,
                platform = excluded.platform,
                updated_at = now()
            """.trimIndent(),
            request.deviceId, familyId, request.expoToken, request.label, request.platform,
        )
        return PushTokenResponse(
            deviceId = request.deviceId,
            familyId = familyId,
            expoToken = request.expoToken,
            label = request.label,
            platform = request.platform,
        )
    }

    fun delete(familyId: String, deviceId: String) {
        jdbc.update(
            "delete from bl_push_tokens where device_id = ? and family_id = ?",
            deviceId, familyId,
        )
    }

    fun tokensForFamilyExcept(familyId: String, excludeDeviceId: String?): List<String> {
        return if (excludeDeviceId == null) {
            jdbc.queryForList(
                "select expo_token from bl_push_tokens where family_id = ?",
                String::class.java, familyId,
            )
        } else {
            jdbc.queryForList(
                "select expo_token from bl_push_tokens where family_id = ? and device_id <> ?",
                String::class.java, familyId, excludeDeviceId,
            )
        }
    }
}
