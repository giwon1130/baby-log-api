package com.giwon.babylog.features.monthlyphoto

import com.giwon.babylog.features.push.ExpoPushSender
import com.giwon.babylog.features.push.PushTokenService
import com.giwon.babylog.features.realtime.FamilyEventBroker
import com.giwon.babylog.features.upload.UploadStorageService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

@Service
class MonthlyPhotoService(
    private val jdbc: JdbcTemplate,
    private val storage: UploadStorageService,
    private val broker: FamilyEventBroker,
    private val pushTokenService: PushTokenService,
    private val expoPushSender: ExpoPushSender,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 해당 아기의 모든 월 증명사진 (보유 슬롯만). 슬롯 1..12 그리드는 클라이언트가 생성. */
    fun list(babyId: String): List<MonthlyPhotoResponse> =
        jdbc.query(
            """
            select id, baby_id, month_index, photo_url, thumbnail_url, storage_key,
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
     * 기존 사진 파일은 best-effort 로 삭제 (실패해도 메타는 갱신).
     * 알림 row 도 같이 잠가서 이후 그 슬롯엔 "찍을 시간" 알림 안 가도록.
     */
    @Transactional
    fun upsert(babyId: String, input: MonthlyPhotoUpsertInput): MonthlyPhotoResponse {
        require(input.monthIndex in 1..12) { "monthIndex 는 1..12 범위여야 해." }
        require(input.photoUrl.isNotBlank()) { "photoUrl 이 비어 있어." }
        val now = OffsetDateTime.now()
        val takenAt = input.takenAt ?: now

        // 기존 row 가 있다면 파일 청소를 위해 storage_key 먼저 챙기기
        val existing = jdbc.query(
            "select id, storage_key from bl_monthly_photos where baby_id = ? and month_index = ?",
            { rs, _ -> rs.getString("id") to rs.getString("storage_key") },
            babyId, input.monthIndex,
        ).firstOrNull()

        if (existing == null) {
            val id = UUID.randomUUID().toString()
            jdbc.update(
                """
                insert into bl_monthly_photos
                  (id, baby_id, month_index, photo_url, thumbnail_url, storage_key,
                   taken_at, caption, location_hint, created_at, updated_at)
                  values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                id, babyId, input.monthIndex, input.photoUrl, input.thumbnailUrl, input.storageKey,
                takenAt, input.caption, input.locationHint, now, now,
            )
        } else {
            val (_, oldKey) = existing
            jdbc.update(
                """
                update bl_monthly_photos
                   set photo_url = ?, thumbnail_url = ?, storage_key = ?,
                       taken_at = ?, caption = ?, location_hint = ?, updated_at = ?
                 where baby_id = ? and month_index = ?
                """.trimIndent(),
                input.photoUrl, input.thumbnailUrl, input.storageKey,
                takenAt, input.caption, input.locationHint, now,
                babyId, input.monthIndex,
            )
            // 새 파일이 다른 key 면 옛 파일 청소
            if (!oldKey.isNullOrBlank() && oldKey != input.storageKey) {
                runCatching { storage.delete(oldKey) }
                    .onFailure { log.warn("storage delete failed key={}", oldKey, it) }
            }
        }

        // 더 이상 알림 안 가도 되니까 idempotency 잠금
        jdbc.update(
            """
            insert into bl_monthly_photo_reminders (baby_id, month_index, sent_at)
            values (?, ?, now())
            on conflict (baby_id, month_index) do nothing
            """.trimIndent(),
            babyId, input.monthIndex,
        )

        val all = list(babyId)
        val saved = all.first { it.monthIndex == input.monthIndex }
        // 가족 다른 디바이스에 실시간 알림 (SSE) — 슬롯 채워졌으니 그리드 새로고침 유도
        broker.publishForBaby(babyId, "MONTHLY_PHOTO_UPSERTED", saved)

        // 12장 모두 채워졌으면 "첫 돌 패키지 완성" 마일스톤 1회 트리거 (idempotency)
        if (all.size == 12 && all.map { it.monthIndex }.toSet() == (1..12).toSet()) {
            triggerFirstYearMilestone(babyId)
        }
        return saved
    }

    /** 12장 다 채워진 순간 1회만 푸시 + SSE 이벤트. idempotency 는 unique row. */
    private fun triggerFirstYearMilestone(babyId: String) {
        val inserted = jdbc.update(
            """
            insert into bl_first_year_notified (baby_id, notified_at)
            values (?, now())
            on conflict (baby_id) do nothing
            """.trimIndent(),
            babyId,
        )
        if (inserted == 0) return  // 이미 발송 — skip

        val info = jdbc.query(
            "select family_id, name from bl_babies where id = ?",
            { rs, _ -> rs.getString("family_id") to rs.getString("name") },
            babyId,
        ).firstOrNull() ?: return

        val (familyId, babyName) = info
        val tokens = pushTokenService.tokensForDailySummary(familyId)
        if (tokens.isNotEmpty()) {
            expoPushSender.send(
                tokens,
                "🎉 첫 돌 패키지 완성!",
                "${babyName}의 12개월 사진이 모두 채워졌어요. 첫 돌 패키지를 만들어보세요.",
                mapOf("type" to "FIRST_YEAR_COMPLETE", "babyId" to babyId, "familyId" to familyId),
            )
        }
        broker.publishForBaby(babyId, "FIRST_YEAR_COMPLETE", mapOf("babyId" to babyId))
        log.info("first-year package milestone fired. familyId={}, babyId={}", familyId, babyId)
    }

    /** 슬롯 삭제 — DB row + 볼륨 파일 모두. */
    @Transactional
    fun delete(babyId: String, monthIndex: Int) {
        require(monthIndex in 1..12) { "monthIndex 는 1..12 범위여야 해." }
        val storageKey = jdbc.query(
            "select storage_key from bl_monthly_photos where baby_id = ? and month_index = ?",
            { rs, _ -> rs.getString("storage_key") },
            babyId, monthIndex,
        ).firstOrNull()

        val deleted = jdbc.update(
            "delete from bl_monthly_photos where baby_id = ? and month_index = ?",
            babyId, monthIndex,
        )
        if (deleted == 0) throw IllegalArgumentException("해당 슬롯에 사진이 없어.")

        // 사용자가 비웠으니 알림 idempotency 도 풀어서 다음 도래일에 알림 다시 가도록.
        jdbc.update(
            "delete from bl_monthly_photo_reminders where baby_id = ? and month_index = ?",
            babyId, monthIndex,
        )

        if (!storageKey.isNullOrBlank()) {
            runCatching { storage.delete(storageKey) }
                .onFailure { log.warn("storage delete failed key={}", storageKey, it) }
        }

        broker.publishForBaby(babyId, "MONTHLY_PHOTO_DELETED", mapOf("monthIndex" to monthIndex))
    }

    private fun ResultSet.toResponse() = MonthlyPhotoResponse(
        id = getString("id"),
        babyId = getString("baby_id"),
        monthIndex = getInt("month_index"),
        photoUrl = getString("photo_url"),
        thumbnailUrl = getString("thumbnail_url"),
        storageKey = getString("storage_key"),
        takenAt = getObject("taken_at", OffsetDateTime::class.java),
        caption = getString("caption"),
        locationHint = getString("location_hint"),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    )
}
