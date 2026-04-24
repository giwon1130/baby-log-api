package com.giwon.babylog.features.cry

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
)

data class ConfirmCryRequest(val confirmedLabel: String, val note: String = "")

// ── Internal feature vector ───────────────────────────────────────────────────

private data class FeatureVector(
    val cryAvg: Double,
    val cryMax: Double,
    val volAvg: Double,
    val volPeak: Double,
    val duration: Double,
) {
    fun distanceTo(other: FeatureVector): Double {
        // Normalized euclidean distance — features are already roughly 0-1 except duration/db
        val dCry = (cryAvg - other.cryAvg)
        val dCryM = (cryMax - other.cryMax)
        val dVol = (volAvg - other.volAvg) / 40.0   // dB range roughly [-60, -20]
        val dVolP = (volPeak - other.volPeak) / 40.0
        val dDur = (duration - other.duration) / 10.0
        return sqrt(dCry * dCry + dCryM * dCryM + dVol * dVol + dVolP * dVolP + dDur * dDur)
    }
}

// ── Service ───────────────────────────────────────────────────────────────────

@Service
class CryAnalysisService(private val jdbc: JdbcTemplate) {

    companion object {
        // Thresholds mark which classifier "stage" we're in based on confirmed sample count.
        const val STAGE_SIMILARITY_MIN = 20
        const val STAGE_PERSONAL_MIN = 50
    }

    /**
     * Persist a new sample, compute predictions, return them.
     * Predictions combine:
     *   1. Context priors (time since last feed/sleep/diaper, age, hour of day)
     *   2. Audio features (high pitch/volume bias toward PAIN; long cry bias toward HUNGER)
     *   3. (≥20 confirmed samples) similarity to this baby's past confirmed samples
     */
    fun submit(babyId: String, request: SubmitCryRequest): CrySampleResponse {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val context = buildContext(babyId, now)
        val features = FeatureVector(
            cryAvg = request.cryConfidenceAvg ?: 0.0,
            cryMax = request.cryConfidenceMax ?: 0.0,
            volAvg = request.avgVolumeDb ?: -50.0,
            volPeak = request.peakVolumeDb ?: -30.0,
            duration = request.durationSec,
        )

        val confirmedHistory = loadConfirmedHistory(babyId)
        val predictions = classify(features, context, confirmedHistory)
        val top = predictions.first()

        val id = UUID.randomUUID().toString()
        jdbc.update(
            """
            insert into bl_cry_samples (
                id, baby_id, recorded_at, duration_sec,
                cry_confidence_avg, cry_confidence_max, avg_volume_db, peak_volume_db,
                minutes_since_last_feed, minutes_since_last_diaper,
                minutes_since_last_sleep_start, minutes_since_last_sleep_end,
                is_during_sleep, baby_age_days, time_of_day_hour,
                predicted_label, predicted_confidence, note
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, babyId, now, request.durationSec,
            request.cryConfidenceAvg, request.cryConfidenceMax,
            request.avgVolumeDb, request.peakVolumeDb,
            context.minutesSinceFeed, context.minutesSinceDiaper,
            context.minutesSinceSleepStart, context.minutesSinceSleepEnd,
            context.isDuringSleep, context.babyAgeDays, context.timeOfDayHour,
            top.label, top.confidence, request.note,
        )

        return CrySampleResponse(
            id = id,
            babyId = babyId,
            recordedAt = now.toString(),
            durationSec = request.durationSec,
            predictions = predictions,
            confirmedLabel = null,
            confirmedLabelDisplay = null,
            learningStage = learningStageFor(confirmedHistory.size),
            note = request.note,
        )
    }

    fun confirm(sampleId: String, request: ConfirmCryRequest): CrySampleResponse {
        require(request.confirmedLabel in CryLabels.ALL || request.confirmedLabel == CryLabels.UNKNOWN) {
            "알 수 없는 라벨: ${request.confirmedLabel}"
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = jdbc.update(
            """update bl_cry_samples
               set confirmed_label = ?, confirmed_at = ?, note = case when ? = '' then note else ? end
               where id = ?""".trimIndent(),
            request.confirmedLabel, now, request.note, request.note, sampleId,
        )
        require(updated > 0) { "울음 기록을 찾을 수 없어요" }
        return getSample(sampleId)
    }

    fun history(babyId: String, limit: Int = 50): List<CrySampleResponse> {
        return jdbc.query(
            """select * from bl_cry_samples where baby_id = ?
               order by recorded_at desc limit ?""".trimIndent(),
            { rs, _ -> toResponseRow(rs, null) },
            babyId, limit,
        )
    }

    fun getSample(id: String): CrySampleResponse {
        return jdbc.queryForObject(
            "select * from bl_cry_samples where id = ?",
            { rs, _ -> toResponseRow(rs, null) },
            id,
        ) ?: throw IllegalArgumentException("울음 기록을 찾을 수 없어요")
    }

    // ── Classifier ────────────────────────────────────────────────────────────

    private fun classify(
        features: FeatureVector,
        context: ContextSnapshot,
        confirmedHistory: List<ConfirmedSample>,
    ): List<CryPrediction> {
        val reasons = mutableMapOf<String, MutableList<String>>()
        CryLabels.ALL.forEach { reasons[it] = mutableListOf() }

        // ── 1. Context-based priors
        val scores = mutableMapOf<String, Double>()

        // HUNGER: rises with time since last feed. Newborns want feed every 2-3h.
        // When sinceFeed is null we treat it as "context unknown" — don't default to HUNGER,
        // otherwise every first-use prediction becomes 배고픔.
        val sinceFeed = context.minutesSinceFeed
        scores[CryLabels.HUNGER] = when {
            sinceFeed == null -> 0.2                                            // unknown → neutral
            sinceFeed >= 210 -> { reasons[CryLabels.HUNGER]!! += "마지막 수유 ${sinceFeed / 60}시간 ${sinceFeed % 60}분 전"; 0.6 }
            sinceFeed >= 150 -> { reasons[CryLabels.HUNGER]!! += "마지막 수유 ${sinceFeed / 60}시간 ${sinceFeed % 60}분 전"; 0.45 }
            sinceFeed >= 90 -> { reasons[CryLabels.HUNGER]!! += "마지막 수유 ${sinceFeed}분 전"; 0.28 }
            sinceFeed >= 45 -> 0.14
            else -> { reasons[CryLabels.HUNGER]!! += "방금 수유함 (${sinceFeed}분 전)"; 0.05 }
        }

        // TIRED: rises with awake time since last sleep end
        val sinceWake = context.minutesSinceSleepEnd
        scores[CryLabels.TIRED] = when {
            context.isDuringSleep -> 0.12                                       // 자는 중엔 낮음
            sinceWake == null -> 0.2
            sinceWake >= 120 -> { reasons[CryLabels.TIRED]!! += "깬 지 ${sinceWake / 60}시간 ${sinceWake % 60}분 경과"; 0.55 }
            sinceWake >= 75 -> { reasons[CryLabels.TIRED]!! += "깬 지 ${sinceWake}분 경과"; 0.4 }
            sinceWake >= 45 -> 0.2
            else -> 0.1
        }

        // DISCOMFORT: rises with time since diaper change
        val sinceDiaper = context.minutesSinceDiaper
        scores[CryLabels.DISCOMFORT] = when {
            sinceDiaper == null -> 0.2
            sinceDiaper >= 180 -> { reasons[CryLabels.DISCOMFORT]!! += "기저귀 간 지 ${sinceDiaper / 60}시간 경과"; 0.5 }
            sinceDiaper >= 120 -> { reasons[CryLabels.DISCOMFORT]!! += "기저귀 간 지 ${sinceDiaper}분 경과"; 0.32 }
            sinceDiaper >= 60 -> 0.2
            else -> 0.1
        }

        // BURP: higher right after a feed (0-30 min window)
        scores[CryLabels.BURP] = when {
            sinceFeed == null -> 0.15
            sinceFeed in 5..30 -> { reasons[CryLabels.BURP]!! += "수유 직후 (${sinceFeed}분 경과)"; 0.5 }
            sinceFeed in 0..5 -> 0.2
            else -> 0.1
        }

        // PAIN: hardest to infer from context. Raised baseline so audio signals
        // (loud/sharp cries) can actually overtake HUNGER.
        scores[CryLabels.PAIN] = 0.15

        // ── 2. Audio feature adjustments (ADDITIVE so audio can beat context)
        if (features.cryMax > 0.85) {
            reasons[CryLabels.PAIN]!! += "매우 강한 울음 (신뢰도 ${(features.cryMax * 100).toInt()}%)"
            scores[CryLabels.PAIN] = scores[CryLabels.PAIN]!! + 0.35
        } else if (features.cryMax > 0.7) {
            reasons[CryLabels.PAIN]!! += "강한 울음 (신뢰도 ${(features.cryMax * 100).toInt()}%)"
            scores[CryLabels.PAIN] = scores[CryLabels.PAIN]!! + 0.18
        }
        if (features.volPeak > -15) {
            reasons[CryLabels.PAIN]!! += "큰 소리"
            scores[CryLabels.PAIN] = scores[CryLabels.PAIN]!! + 0.15
        }
        // 짧고 날카로운 울음 → 통증/놀람 신호
        if (features.duration in 0.5..3.0 && features.cryMax > 0.6) {
            reasons[CryLabels.PAIN]!! += "짧고 날카로운 울음"
            scores[CryLabels.PAIN] = scores[CryLabels.PAIN]!! + 0.12
        }
        // 길고 지속적인 울음 → 배고픔
        if (features.duration >= 10.0 && features.cryAvg > 0.5) {
            reasons[CryLabels.HUNGER]!! += "길고 지속적인 울음"
            scores[CryLabels.HUNGER] = scores[CryLabels.HUNGER]!! + 0.1
        }
        // 낮은 볼륨의 칭얼거림 → 불편함 또는 졸림
        if (features.volAvg < -35 && features.cryAvg < 0.5) {
            reasons[CryLabels.DISCOMFORT]!! += "칭얼거리는 수준"
            scores[CryLabels.DISCOMFORT] = scores[CryLabels.DISCOMFORT]!! + 0.1
            scores[CryLabels.TIRED] = scores[CryLabels.TIRED]!! + 0.08
        }

        // ── 3. Per-baby similarity boost (Phase 2 — needs confirmed history)
        if (confirmedHistory.size >= STAGE_SIMILARITY_MIN) {
            for (label in CryLabels.ALL) {
                val labelSamples = confirmedHistory.filter { it.label == label }
                if (labelSamples.size < 3) continue
                val avgDistance = labelSamples.map { features.distanceTo(it.features) }.average()
                // Convert distance to similarity (closer = higher multiplier, 1.0 ~ 1.4)
                val boost = 1.0 + 0.4 * exp(-avgDistance)
                scores[label] = scores[label]!! * boost
                if (boost > 1.15) {
                    reasons[label]!! += "과거 기록과 유사한 울음 패턴"
                }
            }
        }

        // ── Normalize to probabilities
        val total = scores.values.sum().coerceAtLeast(1e-6)
        val normalized = scores.mapValues { it.value / total }

        return normalized.entries
            .sortedByDescending { it.value }
            .map { (label, conf) ->
                CryPrediction(
                    label = label,
                    labelDisplay = CryLabels.DISPLAY[label] ?: label,
                    confidence = conf,
                    reasons = reasons[label]?.toList() ?: emptyList(),
                )
            }
    }

    // ── Context snapshot ──────────────────────────────────────────────────────

    private data class ContextSnapshot(
        val minutesSinceFeed: Int?,
        val minutesSinceDiaper: Int?,
        val minutesSinceSleepStart: Int?,
        val minutesSinceSleepEnd: Int?,
        val isDuringSleep: Boolean,
        val babyAgeDays: Int?,
        val timeOfDayHour: Int,
    )

    private fun buildContext(babyId: String, now: OffsetDateTime): ContextSnapshot {
        val sinceFeed = minutesSince(
            jdbc.query(
                "select fed_at from bl_feed_records where baby_id = ? order by fed_at desc limit 1",
                { rs, _ -> rs.getObject("fed_at", OffsetDateTime::class.java) }, babyId,
            ).firstOrNull(), now,
        )
        val sinceDiaper = minutesSince(
            jdbc.query(
                "select changed_at from bl_diaper_records where baby_id = ? order by changed_at desc limit 1",
                { rs, _ -> rs.getObject("changed_at", OffsetDateTime::class.java) }, babyId,
            ).firstOrNull(), now,
        )
        val lastSleep = jdbc.query(
            "select slept_at, woke_at from bl_sleep_records where baby_id = ? order by slept_at desc limit 1",
            { rs, _ ->
                Pair(
                    rs.getObject("slept_at", OffsetDateTime::class.java),
                    rs.getObject("woke_at", OffsetDateTime::class.java),
                )
            }, babyId,
        ).firstOrNull()
        val sinceSleepStart = minutesSince(lastSleep?.first, now)
        val sinceSleepEnd = minutesSince(lastSleep?.second, now)
        val isDuringSleep = lastSleep != null && lastSleep.first != null && lastSleep.second == null

        val birthDate = jdbc.query(
            "select birth_date from bl_babies where id = ?",
            { rs, _ -> rs.getObject("birth_date", LocalDate::class.java) }, babyId,
        ).firstOrNull()
        val ageDays = birthDate?.let { Duration.between(it.atStartOfDay(ZoneOffset.UTC).toInstant(), now.toInstant()).toDays().toInt() }

        return ContextSnapshot(
            minutesSinceFeed = sinceFeed,
            minutesSinceDiaper = sinceDiaper,
            minutesSinceSleepStart = sinceSleepStart,
            minutesSinceSleepEnd = sinceSleepEnd,
            isDuringSleep = isDuringSleep,
            babyAgeDays = ageDays,
            timeOfDayHour = now.atZoneSameInstant(ZoneId.of("Asia/Seoul")).hour,
        )
    }

    private fun minutesSince(ts: OffsetDateTime?, now: OffsetDateTime): Int? =
        ts?.let { Duration.between(it, now).toMinutes().toInt().coerceAtLeast(0) }

    // ── Confirmed history ─────────────────────────────────────────────────────

    private data class ConfirmedSample(val label: String, val features: FeatureVector)

    private fun loadConfirmedHistory(babyId: String): List<ConfirmedSample> {
        return jdbc.query(
            """select confirmed_label, duration_sec, cry_confidence_avg, cry_confidence_max,
                      avg_volume_db, peak_volume_db
               from bl_cry_samples
               where baby_id = ? and confirmed_label is not null
               order by confirmed_at desc limit 200""".trimIndent(),
            { rs, _ ->
                ConfirmedSample(
                    label = rs.getString("confirmed_label"),
                    features = FeatureVector(
                        cryAvg = rs.getObject("cry_confidence_avg") as? Double ?: 0.0,
                        cryMax = rs.getObject("cry_confidence_max") as? Double ?: 0.0,
                        volAvg = rs.getObject("avg_volume_db") as? Double ?: -50.0,
                        volPeak = rs.getObject("peak_volume_db") as? Double ?: -30.0,
                        duration = rs.getDouble("duration_sec"),
                    ),
                )
            },
            babyId,
        )
    }

    private fun learningStageFor(confirmedCount: Int): LearningStage = when {
        confirmedCount < STAGE_SIMILARITY_MIN -> LearningStage(
            confirmedCount = confirmedCount,
            stage = "HEURISTIC",
            stageDisplay = "학습 중",
            nextStageAt = STAGE_SIMILARITY_MIN,
            nextStageDisplay = "개인화 시작",
        )
        confirmedCount < STAGE_PERSONAL_MIN -> LearningStage(
            confirmedCount = confirmedCount,
            stage = "SIMILARITY",
            stageDisplay = "개인화 진행 중",
            nextStageAt = STAGE_PERSONAL_MIN,
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

    // ── Row mapping ───────────────────────────────────────────────────────────

    private fun toResponseRow(rs: java.sql.ResultSet, predictions: List<CryPrediction>?): CrySampleResponse {
        val confirmed = rs.getString("confirmed_label")
        val babyId = rs.getString("baby_id")
        val confirmedHistory = if (predictions == null) loadConfirmedHistory(babyId) else emptyList()
        return CrySampleResponse(
            id = rs.getString("id"),
            babyId = babyId,
            recordedAt = rs.getObject("recorded_at", OffsetDateTime::class.java).toString(),
            durationSec = rs.getDouble("duration_sec"),
            predictions = predictions ?: listOf(
                CryPrediction(
                    label = rs.getString("predicted_label"),
                    labelDisplay = CryLabels.DISPLAY[rs.getString("predicted_label")] ?: rs.getString("predicted_label"),
                    confidence = rs.getDouble("predicted_confidence"),
                    reasons = emptyList(),
                ),
            ),
            confirmedLabel = confirmed,
            confirmedLabelDisplay = confirmed?.let { CryLabels.DISPLAY[it] ?: it },
            learningStage = learningStageFor(confirmedHistory.size),
            note = rs.getString("note") ?: "",
        )
    }
}
