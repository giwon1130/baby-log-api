package com.giwon.babylog.features.cry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LearningStageTest {

    // ── 단계 경계값 ───────────────────────────────────────────────────

    @Test
    fun `0건이면 HEURISTIC`() {
        assertEquals("HEURISTIC", LearningStage.from(0).stage)
    }

    @Test
    fun `19건이면 아직 HEURISTIC`() {
        assertEquals("HEURISTIC", LearningStage.from(19).stage)
    }

    @Test
    fun `20건이면 SIMILARITY 로 진입`() {
        assertEquals("SIMILARITY", LearningStage.from(20).stage)
    }

    @Test
    fun `49건이면 아직 SIMILARITY`() {
        assertEquals("SIMILARITY", LearningStage.from(49).stage)
    }

    @Test
    fun `50건이면 PERSONAL 로 진입`() {
        assertEquals("PERSONAL", LearningStage.from(50).stage)
    }

    // ── 다음 단계 안내 ────────────────────────────────────────────────

    @Test
    fun `HEURISTIC 의 다음 단계 기준은 20`() {
        assertEquals(20, LearningStage.from(5).nextStageAt)
    }

    @Test
    fun `SIMILARITY 의 다음 단계 기준은 50`() {
        assertEquals(50, LearningStage.from(30).nextStageAt)
    }

    @Test
    fun `PERSONAL 은 다음 단계가 없다`() {
        val stage = LearningStage.from(80)
        assertNull(stage.nextStageAt)
        assertNull(stage.nextStageDisplay)
    }

    // ── confirmedCount 보존 ───────────────────────────────────────────

    @Test
    fun `입력한 confirmedCount 가 그대로 담긴다`() {
        assertEquals(37, LearningStage.from(37).confirmedCount)
    }
}
