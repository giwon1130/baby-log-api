package com.giwon.babylog.features.baby

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Light-weight read-only lookups for baby/family identifiers.
 *
 * 여러 서비스(브로커, Shortcuts, Cry 분석 등)에서 반복되던
 * `select name from bl_babies` / `select family_id from bl_babies` /
 * 초대 코드 → family_id 해석 SQL 을 한 곳으로 모음.
 */
@Repository
class BabyLookupRepository(private val jdbc: JdbcTemplate) {

    /** 아기 이름 (없으면 null). */
    fun findBabyName(babyId: String): String? = runCatching {
        jdbc.queryForObject("select name from bl_babies where id = ?", String::class.java, babyId)
    }.getOrNull()

    /** 아기 소속 가족 ID (없으면 null). */
    fun findFamilyId(babyId: String): String? = runCatching {
        jdbc.queryForObject(
            "select family_id from bl_babies where id = ?",
            String::class.java,
            babyId,
        )
    }.getOrNull()

    /** 초대 코드 → family_id. 못 찾으면 null. */
    fun findFamilyIdByInviteCode(inviteCode: String): String? = runCatching {
        jdbc.queryForObject(
            "select id from bl_families where invite_code = ?",
            String::class.java,
            inviteCode,
        )
    }.getOrNull()

    /** 가족 안의 모든 아기 (id, name). birth_date 오름차순. */
    fun findBabiesByFamilyId(familyId: String): List<BabyRef> = jdbc.query(
        "select id, name from bl_babies where family_id = ? order by birth_date",
        { rs, _ -> BabyRef(id = rs.getString("id"), name = rs.getString("name")) },
        familyId,
    )
}

data class BabyRef(val id: String, val name: String)
