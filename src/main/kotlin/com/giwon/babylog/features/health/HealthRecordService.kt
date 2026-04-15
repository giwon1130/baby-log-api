package com.giwon.babylog.features.health

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class HealthRecordResponse(
    val id: String,
    val babyId: String,
    val recordedAt: String,
    val type: String,        // TEMPERATURE | MEDICINE
    val value: Double?,      // 체온(°C) or 투여량
    val name: String,        // 약 이름 (체온 시 빈 문자열)
    val note: String,
)

data class CreateHealthRecordRequest(
    val recordedAt: String? = null,
    val type: String,
    val value: Double? = null,
    val name: String = "",
    val note: String = "",
)

@Service
class HealthRecordService(private val jdbc: JdbcTemplate) {

    fun recordHealth(babyId: String, request: CreateHealthRecordRequest): HealthRecordResponse {
        val id = UUID.randomUUID().toString()
        val recordedAt = request.recordedAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC)

        jdbc.update(
            """insert into bl_health_records (id, baby_id, recorded_at, type, value, name, note)
               values (?, ?, ?, ?, ?, ?, ?)""",
            id, babyId, recordedAt, request.type, request.value, request.name, request.note,
        )
        return HealthRecordResponse(
            id = id, babyId = babyId, recordedAt = recordedAt.toString(),
            type = request.type, value = request.value, name = request.name, note = request.note,
        )
    }

    fun getHealthRecords(babyId: String, limit: Int = 50): List<HealthRecordResponse> =
        jdbc.query(
            "select * from bl_health_records where baby_id = ? order by recorded_at desc limit ?",
            { rs, _ ->
                HealthRecordResponse(
                    id = rs.getString("id"),
                    babyId = rs.getString("baby_id"),
                    recordedAt = rs.getObject("recorded_at", OffsetDateTime::class.java).toString(),
                    type = rs.getString("type"),
                    value = rs.getObject("value") as? Double,
                    name = rs.getString("name"),
                    note = rs.getString("note"),
                )
            },
            babyId, limit,
        )

    fun deleteHealthRecord(babyId: String, recordId: String) {
        jdbc.update("delete from bl_health_records where id = ? and baby_id = ?", recordId, babyId)
    }
}
