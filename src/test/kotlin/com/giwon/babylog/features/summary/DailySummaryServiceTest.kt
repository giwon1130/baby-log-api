package com.giwon.babylog.features.summary

import com.giwon.babylog.features.stats.TodayStatsResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class DailySummaryServiceTest {

    // jdbc/stats/push/expo/gemini 는 isEmpty·templateMessage 에서 안 쓰임 — mock 주입
    private val service = DailySummaryService(mock(), mock(), mock(), mock(), mock())

    private fun stats(
        feedCount: Int = 0,
        totalFeedMl: Int = 0,
        diaperCount: Int = 0,
        wetCount: Int = 0,
        dirtyCount: Int = 0,
        sleepCount: Int = 0,
        totalSleepMinutes: Long = 0,
        longestSleepMinutes: Long = 0,
        avgFeedIntervalMinutes: Double? = null,
    ) = TodayStatsResponse(
        date = "2026-05-20",
        feedCount = feedCount,
        totalFeedMl = totalFeedMl,
        diaperCount = diaperCount,
        wetCount = wetCount,
        dirtyCount = dirtyCount,
        sleepCount = sleepCount,
        totalSleepMinutes = totalSleepMinutes,
        longestSleepMinutes = longestSleepMinutes,
        avgFeedIntervalMinutes = avgFeedIntervalMinutes,
    )

    // ── isEmpty ───────────────────────────────────────────────────────

    @Test
    fun `기록이 전혀 없으면 isEmpty true`() {
        assertTrue(service.isEmpty(stats()))
    }

    @Test
    fun `수유 기록만 있어도 isEmpty false`() {
        assertFalse(service.isEmpty(stats(feedCount = 1, totalFeedMl = 80)))
    }

    @Test
    fun `기저귀 기록만 있어도 isEmpty false`() {
        assertFalse(service.isEmpty(stats(diaperCount = 2)))
    }

    @Test
    fun `수면 기록만 있어도 isEmpty false`() {
        assertFalse(service.isEmpty(stats(sleepCount = 1, totalSleepMinutes = 60)))
    }

    // ── templateMessage ──────────────────────────────────────────────

    @Test
    fun `세 가지 모두 있으면 가운뎃점으로 연결`() {
        val msg = service.templateMessage(
            stats(feedCount = 8, totalFeedMl = 720, diaperCount = 6, sleepCount = 4, totalSleepMinutes = 690),
        )
        assertEquals("🍼 수유 8회 · 720ml · 🧷 기저귀 6회 · 😴 수면 11시간 30분", msg)
    }

    @Test
    fun `수유 기록만 있으면 수유 파트만`() {
        val msg = service.templateMessage(stats(feedCount = 3, totalFeedMl = 240))
        assertEquals("🍼 수유 3회 · 240ml", msg)
    }

    @Test
    fun `수면이 1시간 미만이면 분만 표기`() {
        val msg = service.templateMessage(stats(sleepCount = 1, totalSleepMinutes = 45))
        assertEquals("😴 수면 45분", msg)
    }

    @Test
    fun `수면이 정확히 시간 단위면 0분 포함 표기`() {
        val msg = service.templateMessage(stats(sleepCount = 2, totalSleepMinutes = 120))
        assertEquals("😴 수면 2시간 0분", msg)
    }

    @Test
    fun `빈 통계면 빈 문자열`() {
        assertEquals("", service.templateMessage(stats()))
    }
}
