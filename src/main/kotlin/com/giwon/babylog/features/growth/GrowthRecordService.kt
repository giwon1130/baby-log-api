package com.giwon.babylog.features.growth

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class GrowthRecordResponse(
    val id: String,
    val babyId: String,
    val measuredAt: String,
    val weightG: Int?,
    val heightCm: Double?,
    val headCm: Double?,
    val note: String,
)

data class CreateGrowthRecordRequest(
    val measuredAt: String? = null,
    val weightG: Int? = null,
    val heightCm: Double? = null,
    val headCm: Double? = null,
    val note: String = "",
)

@Service
class GrowthRecordService(private val jdbc: JdbcTemplate) {

    fun recordGrowth(babyId: String, request: CreateGrowthRecordRequest): GrowthRecordResponse {
        val id = UUID.randomUUID().toString()
        val measuredAt = request.measuredAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC)

        jdbc.update(
            """insert into bl_growth_records (id, baby_id, measured_at, weight_g, height_cm, head_cm, note)
               values (?, ?, ?, ?, ?, ?, ?)""",
            id, babyId, measuredAt.toString(),
            request.weightG, request.heightCm, request.headCm, request.note,
        )
        return GrowthRecordResponse(
            id = id,
            babyId = babyId,
            measuredAt = measuredAt.toString(),
            weightG = request.weightG,
            heightCm = request.heightCm,
            headCm = request.headCm,
            note = request.note,
        )
    }

    fun getGrowthRecords(babyId: String, limit: Int = 20): List<GrowthRecordResponse> =
        jdbc.query(
            """select * from bl_growth_records where baby_id = ?
               order by measured_at desc limit ?""",
            { rs, _ ->
                GrowthRecordResponse(
                    id = rs.getString("id"),
                    babyId = rs.getString("baby_id"),
                    measuredAt = rs.getString("measured_at"),
                    weightG = rs.getObject("weight_g") as? Int,
                    heightCm = rs.getObject("height_cm") as? Double,
                    headCm = rs.getObject("head_cm") as? Double,
                    note = rs.getString("note"),
                )
            },
            babyId, limit,
        )
}
