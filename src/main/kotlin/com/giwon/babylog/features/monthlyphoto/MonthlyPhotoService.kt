package com.giwon.babylog.features.monthlyphoto

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

@Service
class MonthlyPhotoService(private val jdbc: JdbcTemplate) {

    /** 해당 아기의 모든 월 증명사진 (보유 슬롯만). 슬롯 비어 있는 월차는 클라이언트가 1..12 으로 생성한다. */
    fun list(babyId: String): List<MonthlyPhotoResponse> =
        jdbc.query(
            """
            select id, baby_id, month_index, photo_url, thumbnail_url, cloudinary_public_id,
                   taken_at, caption, location_hint, updated_at
              from bl_monthly_photos
             where baby_id = ?
             order by month_index asc
            """.trimIndent(),
            { rs, _ -> rs.toResponse() },
            babyId,
        )

    /**
     * 슬롯에 사진 저장. 동일 (baby, monthIndex) 가 이미 있으면 덮어쓰기.
     * 알림 row 도 같이 정리 — 사진을 채운 슬롯에 더 이상 "찍을 시간" 알림이 가지 않도록.
     */
    @Transactional
    fun upsert(babyId: String, req: MonthlyPhotoUpsertRequest): MonthlyPhotoResponse {
        require(req.monthIndex in 1..12) { "monthIndex 는 1..12 범위여야 해." }
        require(req.photoUrl.isNotBlank()) { "photoUrl 이 비어 있어." }
        val now = OffsetDateTime.now()
        val takenAt = req.takenAt ?: now

        // 기존 행 검색
        val existingId = jdbc.query(
            "select id from bl_monthly_photos where baby_id = ? and month_index = ?",
            { rs, _ -> rs.getString("id") },
            babyId, req.monthIndex,
        ).firstOrNull()

        if (existingId == null) {
            val id = UUID.randomUUID().toString()
            jdbc.update(
                """
                insert into bl_monthly_photos
                  (id, baby_id, month_index, photo_url, thumbnail_url, cloudinary_public_id,
                   taken_at, caption, location_hint, created_at, updated_at)
                  values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id, babyId, req.monthIndex, req.photoUrl, req.thumbnailUrl, req.cloudinaryPublicId,
                takenAt, req.caption, req.locationHint, now, now,
            )
        } else {
            jdbc.update(
                """
                update bl_monthly_photos
                   set photo_url = ?, thumbnail_url = ?, cloudinary_public_id = ?,
                       taken_at = ?, caption = ?, location_hint = ?, updated_at = ?
                 where id = ?
                """.trimIndent(),
                req.photoUrl, req.thumbnailUrl, req.cloudinaryPublicId,
                takenAt, req.caption, req.locationHint, now, existingId,
            )
        }

        // 더 이상 "찍을 시간" 알림 안 가도 되니까 알림 idempotency row 도 일치시켜 둠 (없으면 insert).
        jdbc.update(
            """
            insert into bl_monthly_photo_reminders (baby_id, month_index, sent_at)
            values (?, ?, now())
            on conflict (baby_id, month_index) do nothing
            """.trimIndent(),
            babyId, req.monthIndex,
        )

        return list(babyId).first { it.monthIndex == req.monthIndex }
    }

    /** 슬롯 삭제 — 백엔드 row 만. Cloudinary 파일은 후속 정리(필요 시) 대상. */
    @Transactional
    fun delete(babyId: String, monthIndex: Int) {
        require(monthIndex in 1..12) { "monthIndex 는 1..12 범위여야 해." }
        val deleted = jdbc.update(
            "delete from bl_monthly_photos where baby_id = ? and month_index = ?",
            babyId, monthIndex,
        )
        if (deleted == 0) throw IllegalArgumentException("해당 슬롯에 사진이 없어.")
        // 사용자가 사진을 비웠으니 알림 idempotency 도 풀어줘서 다음 도래일에 다시 알림 가도록.
        jdbc.update(
            "delete from bl_monthly_photo_reminders where baby_id = ? and month_index = ?",
            babyId, monthIndex,
        )
    }

    private fun ResultSet.toResponse() = MonthlyPhotoResponse(
        id = getString("id"),
        babyId = getString("baby_id"),
        monthIndex = getInt("month_index"),
        photoUrl = getString("photo_url"),
        thumbnailUrl = getString("thumbnail_url"),
        cloudinaryPublicId = getString("cloudinary_public_id"),
        takenAt = getObject("taken_at", OffsetDateTime::class.java),
        caption = getString("caption"),
        locationHint = getString("location_hint"),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    )
}
