package com.giwon.babylog.features.summary

import com.giwon.babylog.features.push.ExpoPushSender
import com.giwon.babylog.features.push.PushTokenService
import com.giwon.babylog.features.stats.StatsService
import com.giwon.babylog.features.stats.TodayStatsResponse
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * 매일 저녁 가족 전체에 그날의 베이비 활동 요약 푸시 발송.
 *
 * 흐름:
 *   1) 가족 단위로 활성 아기 목록 조회
 *   2) 아기별 오늘 통계 (StatsService.getTodayStats)
 *   3) Template 기반 자연어 메시지 (Gemini 도입은 다음 단계)
 *   4) ExpoPushSender 로 가족 전체 디바이스에 푸시
 *
 * 기록이 0건이면 그 가족은 skip — 알림 피로 방지.
 */
@Service
class DailySummaryService(
    private val jdbc: JdbcTemplate,
    private val statsService: StatsService,
    private val pushTokenService: PushTokenService,
    private val expoPushSender: ExpoPushSender,
    private val geminiClient: GeminiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 운영용/스케줄러 진입점. 처리된 가족 수 반환. */
    fun runForAllFamilies(): Int {
        val families = jdbc.queryForList(
            "select id from bl_families",
            String::class.java,
        )
        var sent = 0
        families.forEach { familyId ->
            runCatching { if (runForFamily(familyId)) sent++ }
                .onFailure { log.warn("daily summary failed familyId={}", familyId, it) }
        }
        log.info("daily summary scan done. families={}, sent={}", families.size, sent)
        return sent
    }

    /** 단일 가족 처리. 푸시 발송 성공 시 true. */
    fun runForFamily(familyId: String): Boolean {
        val babies = jdbc.query(
            "select id, name from bl_babies where family_id = ?",
            { rs, _ -> rs.getString("id") to rs.getString("name") },
            familyId,
        )
        if (babies.isEmpty()) return false

        val tokens = pushTokenService.tokensForFamilyExcept(familyId, null)
        if (tokens.isEmpty()) {
            log.info("daily summary skipped — no push tokens. familyId={}", familyId)
            return false
        }

        // 아기가 여러 명이면 첫째 아기 기준으로 요약 (기본). 추후 모든 아기 합산 가능.
        val (babyId, babyName) = babies.first()
        val stats = runCatching { statsService.getTodayStats(babyId) }.getOrNull() ?: return false
        if (isEmpty(stats)) {
            log.info("daily summary skipped — empty stats. familyId={}, babyId={}", familyId, babyId)
            return false
        }

        val title = "📋 ${babyName} 오늘 요약"
        // Gemini 활성 시 자연어 요약, 실패/미설정 시 template fallback
        val body = geminiClient.summarizeDaily(babyName, stats)
            ?: templateMessage(stats)

        expoPushSender.send(
            tokens,
            title,
            body,
            mapOf("type" to "DAILY_SUMMARY", "babyId" to babyId, "familyId" to familyId),
        )
        return true
    }

    private fun isEmpty(s: TodayStatsResponse) =
        s.feedCount == 0 && s.diaperCount == 0 && s.sleepCount == 0

    private fun templateMessage(s: TodayStatsResponse): String {
        val sleepH = s.totalSleepMinutes / 60
        val sleepM = s.totalSleepMinutes % 60
        val sleepStr = if (sleepH > 0) "${sleepH}시간 ${sleepM}분" else "${sleepM}분"

        val parts = mutableListOf<String>()
        if (s.feedCount > 0) parts += "🍼 수유 ${s.feedCount}회 · ${s.totalFeedMl}ml"
        if (s.diaperCount > 0) parts += "🧷 기저귀 ${s.diaperCount}회"
        if (s.sleepCount > 0) parts += "😴 수면 $sleepStr"
        return parts.joinToString(" · ")
    }
}
