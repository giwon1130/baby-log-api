package com.giwon.babylog.features.cry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CryClassifierTest {

    private val classifier = CryClassifier()

    // 모든 오디오 feature 가 중립인 기본 벡터 (submit() 의 미수집 기본값과 동일)
    private fun neutralFeatures(
        cryAvg: Double = 0.0,
        cryMax: Double = 0.0,
        volAvg: Double = -50.0,
        volPeak: Double = -30.0,
        duration: Double = 5.0,
        pitchMean: Double = 0.0,
        pitchStd: Double = 0.0,
        zcrMean: Double = 0.0,
        rhythmicity: Double = 0.0,
    ) = FeatureVector(cryAvg, cryMax, volAvg, volPeak, duration, pitchMean, pitchStd, zcrMean, rhythmicity)

    // 컨텍스트가 전혀 없는 상태 (앱 첫 사용 등)
    private fun emptyContext() = ContextSnapshot(
        minutesSinceFeed = null,
        minutesSinceDiaper = null,
        minutesSinceSleepStart = null,
        minutesSinceSleepEnd = null,
        isDuringSleep = false,
        babyAgeDays = null,
        timeOfDayHour = 12,
    )

    private fun topLabel(predictions: List<CryPrediction>) = predictions.first().label

    // ── 기본 보장 ─────────────────────────────────────────────────────

    @Test
    fun `예측은 5개 라벨을 모두 포함한다`() {
        val result = classifier.classify(neutralFeatures(), emptyContext(), emptyList())
        assertEquals(CryLabels.ALL.toSet(), result.map { it.label }.toSet())
    }

    @Test
    fun `confidence 합은 1에 가깝다`() {
        val result = classifier.classify(neutralFeatures(), emptyContext(), emptyList())
        val sum = result.sumOf { it.confidence }
        assertTrue(sum in 0.99..1.01, "합이 1이어야 하는데 $sum")
    }

    @Test
    fun `예측은 confidence 내림차순으로 정렬된다`() {
        val result = classifier.classify(
            neutralFeatures(pitchMean = 700.0, cryMax = 0.9),
            emptyContext(),
            emptyList(),
        )
        val confidences = result.map { it.confidence }
        assertEquals(confidences.sortedDescending(), confidences)
    }

    // ── 컨텍스트 기반 우선순위 ────────────────────────────────────────

    @Test
    fun `마지막 수유 4시간 전이면 배고픔이 1위`() {
        val ctx = emptyContext().copy(minutesSinceFeed = 240)
        val result = classifier.classify(neutralFeatures(), ctx, emptyList())
        assertEquals(CryLabels.HUNGER, topLabel(result))
    }

    @Test
    fun `깬 지 3시간이면 졸림이 1위`() {
        val ctx = emptyContext().copy(minutesSinceSleepEnd = 180)
        val result = classifier.classify(neutralFeatures(), ctx, emptyList())
        assertEquals(CryLabels.TIRED, topLabel(result))
    }

    @Test
    fun `기저귀 간 지 4시간이면 불편함이 1위`() {
        val ctx = emptyContext().copy(minutesSinceDiaper = 240)
        val result = classifier.classify(neutralFeatures(), ctx, emptyList())
        assertEquals(CryLabels.DISCOMFORT, topLabel(result))
    }

    // ── 오디오 feature 기반 ──────────────────────────────────────────

    @Test
    fun `높은 음조와 강한 울음이면 통증이 1위`() {
        // 컨텍스트는 비어 있고 오디오만 강한 통증 신호
        val features = neutralFeatures(cryMax = 0.95, volPeak = -10.0, pitchMean = 700.0, pitchStd = 150.0)
        val result = classifier.classify(features, emptyContext(), emptyList())
        assertEquals(CryLabels.PAIN, topLabel(result))
    }

    @Test
    fun `방금 수유했으면 배고픔 confidence 가 낮다`() {
        val ctx = emptyContext().copy(minutesSinceFeed = 10)  // 10분 전
        val result = classifier.classify(neutralFeatures(), ctx, emptyList())
        val hunger = result.first { it.label == CryLabels.HUNGER }
        // 방금 먹었으니 배고픔이 1위는 아니어야 함
        assertTrue(topLabel(result) != CryLabels.HUNGER, "방금 수유했는데 배고픔이 1위")
        assertTrue(hunger.confidence < 0.25, "방금 수유 후 배고픔 confidence=${hunger.confidence}")
    }

    @Test
    fun `통증 예측에는 근거 문구가 붙는다`() {
        val features = neutralFeatures(cryMax = 0.95, pitchMean = 700.0)
        val result = classifier.classify(features, emptyContext(), emptyList())
        val pain = result.first { it.label == CryLabels.PAIN }
        assertTrue(pain.reasons.isNotEmpty(), "통증 신호가 강한데 근거가 비어 있음")
    }
}
