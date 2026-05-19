package com.giwon.babylog.features.cry

import org.springframework.stereotype.Component
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 순수 분류기 — DB / IO 의존성 없음. 컨텍스트와 오디오 feature 를 받아
 * 라벨별 확률 분포를 반환한다.
 */
@Component
class CryClassifier {

    fun classify(
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

        // ── 2b. 음향학적 feature 규칙 (피치/리듬/ZCR)
        //
        // 연구 문헌 참고:
        //  - 통증 울음: F0가 높고(>600Hz) 피치 변동 큼, 짧고 날카로움
        //  - 배고픔 울음: 중간 F0(300–500Hz), 규칙적 리듬 (빨-쉬-빨), 길게 지속
        //  - 졸림/칭얼: F0 낮고 길게 끈다, 피치 변동 적음
        //  - 불편함(기저귀 등): ZCR 높은 잡음성 요소 동반 경향
        if (features.pitchMean > 600 && features.pitchMean > 0) {
            reasons[CryLabels.PAIN]!! += "높은 음조 (${features.pitchMean.toInt()}Hz)"
            scores[CryLabels.PAIN] = scores[CryLabels.PAIN]!! + 0.2
        }
        if (features.pitchStd > 120) {
            reasons[CryLabels.PAIN]!! += "피치 변동 큼"
            scores[CryLabels.PAIN] = scores[CryLabels.PAIN]!! + 0.1
        }
        if (features.rhythmicity > 0.45) {
            reasons[CryLabels.HUNGER]!! += "규칙적 리듬"
            scores[CryLabels.HUNGER] = scores[CryLabels.HUNGER]!! + 0.15
        }
        if (features.pitchMean in 0.1..250.0) {
            // 낮고 탁한 음조 → 졸림성 칭얼
            reasons[CryLabels.TIRED]!! += "낮은 음조 (${features.pitchMean.toInt()}Hz)"
            scores[CryLabels.TIRED] = scores[CryLabels.TIRED]!! + 0.12
        }
        if (features.zcrMean > 0.15 && features.volAvg > -35) {
            // 잡음성이 높음 → 물리적 불편(기저귀/옷 쓸림 등) 동반 가능
            reasons[CryLabels.DISCOMFORT]!! += "거친 소리"
            scores[CryLabels.DISCOMFORT] = scores[CryLabels.DISCOMFORT]!! + 0.1
        }

        // ── 3. Per-baby similarity boost (Phase 2 — needs confirmed history)
        if (confirmedHistory.size >= LearningStage.SIMILARITY_MIN) {
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
}

// ── Classifier-internal value types ───────────────────────────────────────────

data class FeatureVector(
    val cryAvg: Double,
    val cryMax: Double,
    val volAvg: Double,
    val volPeak: Double,
    val duration: Double,
    // Acoustic — may be 0 when not captured (old samples)
    val pitchMean: Double,
    val pitchStd: Double,
    val zcrMean: Double,
    val rhythmicity: Double,
) {
    fun distanceTo(other: FeatureVector): Double {
        // Normalized euclidean distance — scale each feature to roughly the same range
        val dCry = (cryAvg - other.cryAvg)
        val dCryM = (cryMax - other.cryMax)
        val dVol = (volAvg - other.volAvg) / 40.0   // dB range roughly [-60, -20]
        val dVolP = (volPeak - other.volPeak) / 40.0
        val dDur = (duration - other.duration) / 10.0
        val dPitch = (pitchMean - other.pitchMean) / 400.0   // baby cry F0 typically 300–800 Hz
        val dPitchStd = (pitchStd - other.pitchStd) / 200.0
        val dZcr = (zcrMean - other.zcrMean)                 // already 0–1
        val dRhythm = (rhythmicity - other.rhythmicity)      // already 0–1
        return sqrt(
            dCry * dCry + dCryM * dCryM + dVol * dVol + dVolP * dVolP + dDur * dDur +
                dPitch * dPitch + dPitchStd * dPitchStd + dZcr * dZcr + dRhythm * dRhythm
        )
    }
}

data class ContextSnapshot(
    val minutesSinceFeed: Int?,
    val minutesSinceDiaper: Int?,
    val minutesSinceSleepStart: Int?,
    val minutesSinceSleepEnd: Int?,
    val isDuringSleep: Boolean,
    val babyAgeDays: Int?,
    val timeOfDayHour: Int,
)

data class ConfirmedSample(val label: String, val features: FeatureVector)
