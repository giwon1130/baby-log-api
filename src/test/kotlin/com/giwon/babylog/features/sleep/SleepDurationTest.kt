package com.giwon.babylog.features.sleep

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SleepDurationTest {

    private val service = SleepService(mock())

    private fun time(hour: Int, minute: Int = 0): OffsetDateTime =
        OffsetDateTime.of(2025, 4, 14, hour, minute, 0, 0, ZoneOffset.UTC)

    // ── 수면 시간 계산 ──────────────────────────────────────────────

    @Test
    fun `1시간 수면 - 60분`() {
        assertEquals(60L, service.calculateSleepDuration(time(22, 0), time(23, 0)))
    }

    @Test
    fun `2시간 30분 수면 - 150분`() {
        assertEquals(150L, service.calculateSleepDuration(time(20, 0), time(22, 30)))
    }

    @Test
    fun `30분 낮잠 - 30분`() {
        assertEquals(30L, service.calculateSleepDuration(time(13, 0), time(13, 30)))
    }

    @Test
    fun `자정 넘기는 수면 - 정확한 분 계산`() {
        val sleptAt = time(23, 30)
        val wokeAt = OffsetDateTime.of(2025, 4, 15, 1, 30, 0, 0, ZoneOffset.UTC)
        assertEquals(120L, service.calculateSleepDuration(sleptAt, wokeAt))
    }

    @Test
    fun `잠든 시각과 깬 시각 동일 - 0분`() {
        assertEquals(0L, service.calculateSleepDuration(time(10, 0), time(10, 0)))
    }

    @Test
    fun `wokeAt null이면 durationMinutes null - 아직 수면 중`() {
        assertNull(service.calculateSleepDuration(time(22, 0), null))
    }

    @Test
    fun `8시간 수면 - 480분`() {
        assertEquals(480L, service.calculateSleepDuration(time(22, 0), time(6, 0).plusDays(1)))
    }

    @Test
    fun `1분 수면 - 1분`() {
        assertEquals(1L, service.calculateSleepDuration(time(10, 0), time(10, 1)))
    }
}
