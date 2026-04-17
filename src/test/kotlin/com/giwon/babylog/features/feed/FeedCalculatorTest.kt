package com.giwon.babylog.features.feed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class FeedCalculatorTest {

    // JdbcTemplate은 사용하지 않지만 생성자에 필요 — mock으로 주입
    private val service = FeedService(mock())

    // ── 분유(FORMULA) 수유량별 간격 ─────────────────────────────────

    @Test
    fun `분유 60ml 이하 - 2시간 간격`() {
        assertEquals(2.0, service.calculateNextFeedInterval(60, "FORMULA"))
    }

    @Test
    fun `분유 30ml - 2시간 간격`() {
        assertEquals(2.0, service.calculateNextFeedInterval(30, "FORMULA"))
    }

    @Test
    fun `분유 0ml - 2시간 간격`() {
        assertEquals(2.0, service.calculateNextFeedInterval(0, "FORMULA"))
    }

    @Test
    fun `분유 61ml - 2_5시간 간격`() {
        assertEquals(2.5, service.calculateNextFeedInterval(61, "FORMULA"))
    }

    @Test
    fun `분유 90ml - 2_5시간 간격`() {
        assertEquals(2.5, service.calculateNextFeedInterval(90, "FORMULA"))
    }

    @Test
    fun `분유 91ml - 3시간 간격`() {
        assertEquals(3.0, service.calculateNextFeedInterval(91, "FORMULA"))
    }

    @Test
    fun `분유 120ml - 3시간 간격`() {
        assertEquals(3.0, service.calculateNextFeedInterval(120, "FORMULA"))
    }

    @Test
    fun `분유 121ml - 3_5시간 간격`() {
        assertEquals(3.5, service.calculateNextFeedInterval(121, "FORMULA"))
    }

    @Test
    fun `분유 150ml - 3_5시간 간격`() {
        assertEquals(3.5, service.calculateNextFeedInterval(150, "FORMULA"))
    }

    // ── 모유(BREAST) 수유 시간별 간격 ──────────────────────────────

    @Test
    fun `모유 좌우 합계 15분 이상 - 2_5시간 간격`() {
        assertEquals(2.5, service.calculateNextFeedInterval(0, "BREAST", leftMinutes = 8.0, rightMinutes = 8.0))
    }

    @Test
    fun `모유 좌우 합계 정확히 15분 - 2_5시간 간격`() {
        assertEquals(2.5, service.calculateNextFeedInterval(0, "BREAST", leftMinutes = 10.0, rightMinutes = 5.0))
    }

    @Test
    fun `모유 좌우 합계 14분 - 2시간 간격`() {
        assertEquals(2.0, service.calculateNextFeedInterval(0, "BREAST", leftMinutes = 7.0, rightMinutes = 7.0))
    }

    @Test
    fun `모유 한쪽만 15분 이상 - 2_5시간 간격`() {
        assertEquals(2.5, service.calculateNextFeedInterval(0, "BREAST", leftMinutes = 20.0, rightMinutes = null))
    }

    @Test
    fun `모유 시간 미입력 - 2시간 간격`() {
        assertEquals(2.0, service.calculateNextFeedInterval(0, "BREAST", leftMinutes = null, rightMinutes = null))
    }

    // ── 혼합(MIXED) 수유 ────────────────────────────────────────────

    @Test
    fun `혼합 수유 합계 15분 이상 - 2_5시간 간격`() {
        assertEquals(2.5, service.calculateNextFeedInterval(60, "MIXED", leftMinutes = 10.0, rightMinutes = 10.0))
    }

    @Test
    fun `혼합 수유 합계 10분 - 2시간 간격`() {
        assertEquals(2.0, service.calculateNextFeedInterval(60, "MIXED", leftMinutes = 5.0, rightMinutes = 5.0))
    }

    // ── feedType 기본값 ─────────────────────────────────────────────

    @Test
    fun `feedType 기본값 FORMULA - 수유량 기준 적용`() {
        assertEquals(3.5, service.calculateNextFeedInterval(150))
    }
}
