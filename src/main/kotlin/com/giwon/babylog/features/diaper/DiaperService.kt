package com.giwon.babylog.features.diaper

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class DiaperResponse(
    val id: String,
    val babyId: String,
    val changedAt: String,
    val diaperType: String,
    val note: String,
)

data class CreateDiaperRequest(
    val changedAt: String? = null,
    val diaperType: String = "WET",   // WET | DIRTY | MIXED | DRY
    val note: String = "",
)

@Service
class DiaperService(private val jdbc: JdbcTemplate) {

    fun recordDiaper(babyId: String, request: CreateDiaperRequest): DiaperResponse {
        val id = UUID.randomUUID().toString()
        val changedAt = request.changedAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC)

        jdbc.update(
            """insert into bl_diaper_records (id, baby_id, changed_at, diaper_type, note)
               values (?, ?, ?, ?, ?)""",
            id, babyId, changedAt.toString(), request.diaperType, request.note,
        )
        return DiaperResponse(
            id = id,
            babyId = babyId,
            changedAt = changedAt.toString(),
            diaperType = request.diaperType,
            note = request.note,
        )
    }

    fun getDiapers(babyId: String, limit: Int = 20): List<DiaperResponse> =
        jdbc.query(
            """select * from bl_diaper_records where baby_id = ?
               order by changed_at desc limit ?""",
            { rs, _ ->
                DiaperResponse(
                    id = rs.getString("id"),
                    babyId = rs.getString("baby_id"),
                    changedAt = rs.getString("changed_at"),
                    diaperType = rs.getString("diaper_type"),
                    note = rs.getString("note"),
                )
            },
            babyId, limit,
        )

    fun getLatestDiaper(babyId: String): DiaperResponse? =
        runCatching {
            jdbc.queryForObject(
                "select * from bl_diaper_records where baby_id = ? order by changed_at desc limit 1",
                { rs, _ ->
                    DiaperResponse(
                        id = rs.getString("id"),
                        babyId = rs.getString("baby_id"),
                        changedAt = rs.getString("changed_at"),
                        diaperType = rs.getString("diaper_type"),
                        note = rs.getString("note"),
                    )
                },
                babyId,
            )
        }.getOrNull()
}
