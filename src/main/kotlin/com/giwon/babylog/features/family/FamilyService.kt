package com.giwon.babylog.features.family

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    fun getFamily(familyId: String): FamilyResponse {
        return jdbc.queryForObject(
            "select id, invite_code from bl_families where id = ?",
            { rs, _ -> FamilyResponse(rs.getString("id"), rs.getString("invite_code")) },
            familyId,
        ) ?: throw IllegalArgumentException("가족을 찾을 수 없어.")
    }

    fun joinFamily(inviteCode: String): FamilyResponse {
        return jdbc.queryForObject(
            "select id, invite_code from bl_families where invite_code = ?",
            { rs, _ -> FamilyResponse(rs.getString("id"), rs.getString("invite_code")) },
            inviteCode,
        ) ?: throw IllegalArgumentException("초대 코드를 찾을 수 없어.")
    }

    /**
     * 빈 가족(소속 아기 0명)만 안전하게 삭제.
     * 아기가 남아 있으면 거부 — 졸업으로 먼저 정리하도록 유도.
     * push token row 도 함께 정리 (FK 위반 방지).
     */
    @Transactional
    fun deleteEmptyFamily(familyId: String) {
        val babyCount: Int = jdbc.queryForObject(
            "select count(*) from bl_babies where family_id = ?",
            Int::class.java,
            familyId,
        )
        if (babyCount > 0) {
            throw IllegalStateException("아기가 ${babyCount}명 남아 있어 가족을 정리할 수 없어. 먼저 졸업 처리해줘.")
        }

        jdbc.update("delete from bl_push_tokens where family_id = ?", familyId)
        val deleted = jdbc.update("delete from bl_families where id = ?", familyId)
        if (deleted == 0) throw IllegalArgumentException("가족을 찾을 수 없어.")
    }

    /**
     * 빈 가족을 한 번에 정리. 운영 청소용.
     * @return 삭제된 가족 수
     */
    @Transactional
    fun cleanupEmptyFamilies(): Int {
        // 빈 가족 후보를 먼저 잡아 push token 정리 → 가족 row 삭제 순서.
        val emptyFamilyIds = jdbc.queryForList(
            """
            select f.id from bl_families f
            where not exists (select 1 from bl_babies b where b.family_id = f.id)
            """.trimIndent(),
            String::class.java,
        )
        if (emptyFamilyIds.isEmpty()) return 0

        emptyFamilyIds.forEach { fid ->
            jdbc.update("delete from bl_push_tokens where family_id = ?", fid)
        }
        return jdbc.update(
            """
            delete from bl_families
            where not exists (select 1 from bl_babies b where b.family_id = bl_families.id)
            """.trimIndent(),
        )
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }
}
