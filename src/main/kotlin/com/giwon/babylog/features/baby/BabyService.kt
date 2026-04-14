package com.giwon.babylog.features.baby

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class BabyResponse(
    val id: String,
    val familyId: String,
    val name: String,
    val birthDate: String,
    val gender: String,
    val birthWeightG: Int?,
    val birthHeightCm: Double?,
    val daysOld: Long,
)

data class CreateBabyRequest(
    val name: String,
    val birthDate: String,
    val gender: String,
    val birthWeightG: Int? = null,
    val birthHeightCm: Double? = null,
)

data class UpdateBabyRequest(
    val name: String? = null,
    val birthWeightG: Int? = null,
    val birthHeightCm: Double? = null,
)

@Service
class BabyService(private val jdbc: JdbcTemplate) {

    fun createBaby(familyId: String, request: CreateBabyRequest): BabyResponse {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """insert into bl_babies (id, family_id, name, birth_date, gender, birth_weight_g, birth_height_cm)
               values (?, ?, ?, ?, ?, ?, ?)""",
            id, familyId, request.name, LocalDate.parse(request.birthDate),
            request.gender, request.birthWeightG, request.birthHeightCm,
        )
        return getBaby(familyId, id)
    }

    fun getBabies(familyId: String): List<BabyResponse> =
        jdbc.query(
            "select * from bl_babies where family_id = ? order by birth_date",
            { rs, _ -> rs.toBabyResponse() },
            familyId,
        )

    fun updateBaby(familyId: String, babyId: String, request: UpdateBabyRequest): BabyResponse {
        val sets = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        request.name?.let { sets += "name = ?"; params += it }
        if (request.birthWeightG != null) { sets += "birth_weight_g = ?"; params += request.birthWeightG }
        if (request.birthHeightCm != null) { sets += "birth_height_cm = ?"; params += request.birthHeightCm }
        if (sets.isEmpty()) return getBaby(familyId, babyId)
        params += babyId; params += familyId
        jdbc.update("update bl_babies set ${sets.joinToString()} where id = ? and family_id = ?", *params.toTypedArray())
        return getBaby(familyId, babyId)
    }

    fun getBaby(familyId: String, babyId: String): BabyResponse =
        jdbc.queryForObject(
            "select * from bl_babies where id = ? and family_id = ?",
            { rs, _ -> rs.toBabyResponse() },
            babyId, familyId,
        ) ?: throw IllegalArgumentException("아기를 찾을 수 없어.")

    private fun java.sql.ResultSet.toBabyResponse(): BabyResponse {
        val birthDate = LocalDate.parse(getString("birth_date"))
        val daysOld = ChronoUnit.DAYS.between(birthDate, LocalDate.now())
        return BabyResponse(
            id = getString("id"),
            familyId = getString("family_id"),
            name = getString("name"),
            birthDate = getString("birth_date"),
            gender = getString("gender"),
            birthWeightG = getObject("birth_weight_g") as? Int,
            birthHeightCm = getObject("birth_height_cm") as? Double,
            daysOld = daysOld,
        )
    }
}
