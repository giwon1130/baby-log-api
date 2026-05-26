package com.giwon.babylog.features.monthlyphoto

import com.giwon.babylog.features.push.ExpoPushSender
import com.giwon.babylog.features.push.PushTokenService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 매일 1회 — 아기별로 다음 미완료 월차 슬롯의 도래일(birth+N개월)이 오늘이고
 *   1) 그 슬롯에 사진이 아직 없고
 *   2) 알림이 아직 안 발송됐을 때
 * 가족 푸시 토큰 전체에 1회 알림.
 *
 * idempotency 는 `bl_monthly_photo_reminders` UNIQUE 인덱스로 보장.
 */
@Service
class MonthlyPhotoReminderService(
    private val jdbc: JdbcTemplate,
    private val pushTokenService: PushTokenService,
    private val expoPushSender: ExpoPushSender,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val kst = ZoneId.of("Asia/Seoul")

    /** @return 발송된 푸시 가족 수 */
    @Transactional
    fun runDaily(): Int {
        val today = LocalDate.now(kst)
        val babies = jdbc.query(
            "select id, family_id, name, birth_date from bl_babies",
            { rs, _ ->
                BabyRow(
                    id = rs.getString("id"),
                    familyId = rs.getString("family_id"),
                    name = rs.getString("name"),
                    birthDate = LocalDate.parse(rs.getString("birth_date")),
                )
            },
        )
        if (babies.isEmpty()) {
            log.info("monthly-photo reminder: no babies, skip")
            return 0
        }

        var sent = 0
        babies.forEach { baby ->
            for (m in 1..12) {
                val due = baby.birthDate.plusMonths(m.toLong())
                if (due != today) continue
                // 이미 사진 채워져 있나?
                val hasPhoto = jdbc.queryForObject(
                    "select count(*) from bl_monthly_photos where baby_id = ? and month_index = ?",
                    Int::class.java, baby.id, m,
                ) > 0
                if (hasPhoto) continue

                // ON CONFLICT DO NOTHING — 이미 보낸 적 있으면 0 반환
                val inserted = jdbc.update(
                    """
                    insert into bl_monthly_photo_reminders (baby_id, month_index, sent_at)
                    values (?, ?, now())
                    on conflict (baby_id, month_index) do nothing
                    """.trimIndent(),
                    baby.id, m,
                )
                if (inserted == 0) continue // 중복 = 이미 보냄

                val tokens = pushTokenService.tokensForDailySummary(baby.familyId)
                if (tokens.isEmpty()) {
                    log.info(
                        "monthly-photo reminder: no opted-in tokens. familyId={}, babyId={}, month={}",
                        baby.familyId, baby.id, m,
                    )
                    continue
                }

                val title = "💝 ${baby.name} ${m}개월 증명사진"
                val body = "오늘이 ${m}개월차 도래일이에요. 평소 장소에서 한 컷 찍어볼까요?"
                expoPushSender.send(
                    tokens, title, body,
                    mapOf(
                        "type" to "MONTHLY_PHOTO_REMINDER",
                        "babyId" to baby.id,
                        "familyId" to baby.familyId,
                        "monthIndex" to m,
                    ),
                )
                log.info(
                    "monthly-photo reminder sent. familyId={}, babyId={}, month={}",
                    baby.familyId, baby.id, m,
                )
                sent++
            }
        }
        log.info("monthly-photo reminder done. babies={}, sent={}", babies.size, sent)
        return sent
    }

    /** 누락분 회복용 — 도래일이 오늘보다 과거이고 아직 사진/알림 둘 다 없는 슬롯도 잡아줌. */
    @Transactional
    fun runCatchup(): Int {
        val today = LocalDate.now(kst)
        val babies = jdbc.query(
            "select id, family_id, name, birth_date from bl_babies",
            { rs, _ ->
                BabyRow(
                    id = rs.getString("id"),
                    familyId = rs.getString("family_id"),
                    name = rs.getString("name"),
                    birthDate = LocalDate.parse(rs.getString("birth_date")),
                )
            },
        )
        var sent = 0
        babies.forEach { baby ->
            for (m in 1..12) {
                val due = baby.birthDate.plusMonths(m.toLong())
                // 과거에 도래했고 아직 30일 이내 즈음만 잡음 (너무 옛 슬롯 새삼 알림 피함)
                val daysOverdue = ChronoUnit.DAYS.between(due, today)
                if (daysOverdue !in 0..30) continue
                val hasPhoto = jdbc.queryForObject(
                    "select count(*) from bl_monthly_photos where baby_id = ? and month_index = ?",
                    Int::class.java, baby.id, m,
                ) > 0
                if (hasPhoto) continue

                val inserted = jdbc.update(
                    """
                    insert into bl_monthly_photo_reminders (baby_id, month_index, sent_at)
                    values (?, ?, now())
                    on conflict (baby_id, month_index) do nothing
                    """.trimIndent(),
                    baby.id, m,
                )
                if (inserted == 0) continue

                val tokens = pushTokenService.tokensForDailySummary(baby.familyId)
                if (tokens.isEmpty()) continue

                val title = "💝 ${baby.name} ${m}개월 증명사진"
                val body = "지난 ${daysOverdue}일 전이 ${m}개월차였어요. 늦었어도 한 컷 어떨까요?"
                expoPushSender.send(
                    tokens, title, body,
                    mapOf(
                        "type" to "MONTHLY_PHOTO_REMINDER",
                        "babyId" to baby.id,
                        "familyId" to baby.familyId,
                        "monthIndex" to m,
                    ),
                )
                sent++
            }
        }
        return sent
    }

    private data class BabyRow(
        val id: String,
        val familyId: String,
        val name: String,
        val birthDate: LocalDate,
    )
}
