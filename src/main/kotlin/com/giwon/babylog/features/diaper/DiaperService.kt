package com.giwon.babylog.features.diaper

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
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
    val diaperType: String = "WET",
    val note: String = "",
)

data class UpdateDiaperRequest(
    val changedAt: String? = null,
    val diaperType: String? = null,
    val note: String? = null,
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
            id = id, babyId = babyId, changedAt = changedAt.toString(),
            diaperType = request.diaperType, note = request.note,
        )
    }

    fun getDiapers(babyId: String, limit: Int = 50, date: String? = null): List<DiaperResponse> {
        val (sql, params) = if (date != null) {
            val start = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toString()
            val end = LocalDate.parse(date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toString()
            """select * from bl_diaper_records where baby_id = ? and changed_at >= ? and changed_at < ?
               order by changed_at desc limit ?""" to arrayOf<Any>(babyId, start, end, limit)
        } else {
            """select * from bl_diaper_records where baby_id = ?
               order by changed_at desc limit ?""" to arrayOf<Any>(babyId, limit)
        }
        return jdbc.query(sql, { rs, _ ->
            DiaperResponse(
                id = rs.getString("id"),
                babyId = rs.getString("baby_id"),
                changedAt = rs.getString("changed_at"),
                diaperType = rs.getString("diaper_type"),
                note = rs.getString("note"),
            )
        }, *params)
    }

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

    fun updateDiaper(babyId: String, diaperId: String, request: UpdateDiaperRequest): DiaperResponse {
        val current = jdbc.queryForObject(
            "select * from bl_diaper_records where id = ? and baby_id = ?",
            { rs, _ -> DiaperResponse(
                id = rs.getString("id"), babyId = rs.getString("baby_id"),
                changedAt = rs.getString("changed_at"), diaperType = rs.getString("diaper_type"),
                note = rs.getString("note"),
            )},
            diaperId, babyId,
        ) ?: throw IllegalArgumentException("기저귀 기록을 찾을 수 없어요")

        val newChangedAt = request.changedAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.parse(current.changedAt)
        val newType = request.diaperType ?: current.diaperType
        val newNote = request.note ?: current.note

        jdbc.update(
            "update bl_diaper_records set changed_at = ?, diaper_type = ?, note = ? where id = ? and baby_id = ?",
            newChangedAt.toString(), newType, newNote, diaperId, babyId,
        )
        return current.copy(changedAt = newChangedAt.toString(), diaperType = newType, note = newNote)
    }

    fun deleteDiaper(babyId: String, diaperId: String) {
        jdbc.update("delete from bl_diaper_records where id = ? and baby_id = ?", diaperId, babyId)
    }
}
