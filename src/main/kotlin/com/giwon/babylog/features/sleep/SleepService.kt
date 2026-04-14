package com.giwon.babylog.features.sleep

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class SleepResponse(
    val id: String,
    val babyId: String,
    val sleptAt: String,
    val wokeAt: String?,
    val durationMinutes: Long?,   // null = 아직 자는 중
    val note: String,
)

data class StartSleepRequest(
    val sleptAt: String? = null,
    val note: String = "",
)

data class EndSleepRequest(
    val wokeAt: String? = null,
)

data class UpdateSleepRequest(
    val sleptAt: String? = null,
    val wokeAt: String? = null,
    val note: String? = null,
)

@Service
class SleepService(private val jdbc: JdbcTemplate) {

    fun startSleep(babyId: String, request: StartSleepRequest): SleepResponse {
        val id = UUID.randomUUID().toString()
        val sleptAt = request.sleptAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC)

        jdbc.update(
            """insert into bl_sleep_records (id, baby_id, slept_at, note)
               values (?, ?, ?, ?)""",
            id, babyId, sleptAt, request.note,
        )
        return SleepResponse(
            id = id,
            babyId = babyId,
            sleptAt = sleptAt.toString(),
            wokeAt = null,
            durationMinutes = null,
            note = request.note,
        )
    }

    fun endSleep(babyId: String, sleepId: String, request: EndSleepRequest): SleepResponse {
        val wokeAt = request.wokeAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC)

        jdbc.update(
            "update bl_sleep_records set woke_at = ? where id = ? and baby_id = ?",
            wokeAt, sleepId, babyId,
        )
        return getSleep(babyId, sleepId)
    }

    fun getSleepRecords(babyId: String, limit: Int = 20): List<SleepResponse> =
        jdbc.query(
            """select * from bl_sleep_records where baby_id = ?
               order by slept_at desc limit ?""",
            { rs, _ -> rs.toSleepResponse() },
            babyId, limit,
        )

    fun getActiveSleep(babyId: String): SleepResponse? =
        runCatching {
            jdbc.queryForObject(
                """select * from bl_sleep_records
                   where baby_id = ? and woke_at is null
                   order by slept_at desc limit 1""",
                { rs, _ -> rs.toSleepResponse() },
                babyId,
            )
        }.getOrNull()

    fun updateSleep(babyId: String, sleepId: String, request: UpdateSleepRequest): SleepResponse {
        val current = getSleep(babyId, sleepId)
        val newSleptAt = request.sleptAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.parse(current.sleptAt)
        val newWokeAt = when {
            request.wokeAt != null -> OffsetDateTime.parse(request.wokeAt)
            current.wokeAt != null -> OffsetDateTime.parse(current.wokeAt)
            else -> null
        }
        val newNote = request.note ?: current.note

        jdbc.update(
            "update bl_sleep_records set slept_at = ?, woke_at = ?, note = ? where id = ? and baby_id = ?",
            newSleptAt, newWokeAt, newNote, sleepId, babyId,
        )
        return getSleep(babyId, sleepId)
    }

    fun deleteSleep(babyId: String, sleepId: String) {
        jdbc.update("delete from bl_sleep_records where id = ? and baby_id = ?", sleepId, babyId)
    }

    private fun getSleep(babyId: String, sleepId: String): SleepResponse =
        jdbc.queryForObject(
            "select * from bl_sleep_records where id = ? and baby_id = ?",
            { rs, _ -> rs.toSleepResponse() },
            sleepId, babyId,
        ) ?: throw IllegalArgumentException("수면 기록을 찾을 수 없어.")

    private fun java.sql.ResultSet.toSleepResponse(): SleepResponse {
        val sleptAt = getObject("slept_at", OffsetDateTime::class.java)
        val wokeAt = getObject("woke_at", OffsetDateTime::class.java)
        val duration = wokeAt?.let { Duration.between(sleptAt, it).toMinutes() }
        return SleepResponse(
            id = getString("id"),
            babyId = getString("baby_id"),
            sleptAt = sleptAt.toString(),
            wokeAt = wokeAt?.toString(),
            durationMinutes = duration,
            note = getString("note"),
        )
    }
}
