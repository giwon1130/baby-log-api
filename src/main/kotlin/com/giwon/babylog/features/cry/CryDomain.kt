package com.giwon.babylog.features.cry

// ── Labels ────────────────────────────────────────────────────────────────────
// Keep this set small and meaningful; adding labels later is fine but every new
// label dilutes the per-baby learning signal.

object CryLabels {
    const val HUNGER = "HUNGER"
    const val TIRED = "TIRED"
    const val DISCOMFORT = "DISCOMFORT"   // wet/dirty diaper, too hot/cold
    const val BURP = "BURP"               // gas / need to burp
    const val PAIN = "PAIN"               // sharp, high-pitched
    const val UNKNOWN = "UNKNOWN"

    val ALL = listOf(HUNGER, TIRED, DISCOMFORT, BURP, PAIN)
    val DISPLAY = mapOf(
        HUNGER to "배고픔",
        TIRED to "졸림",
        DISCOMFORT to "불편함",
        BURP to "트림 필요",
        PAIN to "통증",
        UNKNOWN to "알 수 없음",
    )
}

// ── DTOs ──────────────────────────────────────────────────────────────────────

/** What the app sends after recording: raw features extracted on-device. */
data class SubmitCryRequest(
    val durationSec: Double,
    val cryConfidenceAvg: Double? = null,
    val cryConfidenceMax: Double? = null,
    val avgVolumeDb: Double? = null,
    val peakVolumeDb: Double? = null,
    // Phase 2A: richer acoustic features
    val pitchMeanHz: Double? = null,
    val pitchStdHz: Double? = null,
    val pitchMaxHz: Double? = null,
    val voicedRatio: Double? = null,
    val zcrMean: Double? = null,
    val rhythmicity: Double? = null,
    val note: String = "",
)

/** A single {label, confidence, reason} prediction line. */
data class CryPrediction(
    val label: String,
    val labelDisplay: String,
    val confidence: Double,       // [0, 1], sums to ~1 across the list
    val reasons: List<String>,    // human-readable justifications
)

data class CrySampleResponse(
    val id: String,
    val babyId: String,
    val recordedAt: String,
    val durationSec: Double,
    val predictions: List<CryPrediction>,
    val confirmedLabel: String?,
    val confirmedLabelDisplay: String?,
    val learningStage: LearningStage,
    val note: String,
)

data class LearningStage(
    val confirmedCount: Int,
    val stage: String,                // "HEURISTIC" | "SIMILARITY" | "PERSONAL"
    val stageDisplay: String,         // "학습 중" | "개인화 시작" | "개인화 완료"
    val nextStageAt: Int?,            // sample count needed to reach next stage (null if maxed)
    val nextStageDisplay: String?,
) {
    companion object {
        // Thresholds mark which classifier "stage" we're in based on confirmed sample count.
        const val SIMILARITY_MIN = 20
        const val PERSONAL_MIN = 50

        fun from(confirmedCount: Int): LearningStage = when {
            confirmedCount < SIMILARITY_MIN -> LearningStage(
                confirmedCount = confirmedCount,
                stage = "HEURISTIC",
                stageDisplay = "학습 중",
                nextStageAt = SIMILARITY_MIN,
                nextStageDisplay = "개인화 시작",
            )
            confirmedCount < PERSONAL_MIN -> LearningStage(
                confirmedCount = confirmedCount,
                stage = "SIMILARITY",
                stageDisplay = "개인화 진행 중",
                nextStageAt = PERSONAL_MIN,
                nextStageDisplay = "정밀 분석",
            )
            else -> LearningStage(
                confirmedCount = confirmedCount,
                stage = "PERSONAL",
                stageDisplay = "정밀 분석",
                nextStageAt = null,
                nextStageDisplay = null,
            )
        }
    }
}

data class ConfirmCryRequest(val confirmedLabel: String, val note: String = "")
