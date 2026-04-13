package com.giwon.babylog.features.family

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

data class FamilyResponse(val id: String, val inviteCode: String)

@Service
class FamilyService(private val jdbc: JdbcTemplate) {

    fun createFamily(): FamilyResponse {
        val id = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()
        jdbc.update(
            "insert into bl_families (id, invite_code) values (?, ?)",
            id, inviteCode,
        )
        return FamilyResponse(id, inviteCode)
    }

    fun joinFamily(inviteCode: String): FamilyResponse {
        return jdbc.queryForObject(
            "select id, invite_code from bl_families where invite_code = ?",
            { rs, _ -> FamilyResponse(rs.getString("id"), rs.getString("invite_code")) },
            inviteCode,
        ) ?: throw IllegalArgumentException("초대 코드를 찾을 수 없어.")
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}
