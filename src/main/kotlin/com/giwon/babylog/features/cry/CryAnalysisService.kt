package com.giwon.babylog.features.cry

import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * 울음 분석 오케스트레이션.
 *
 * 흐름:
 *   1) 컨텍스트 + 확정 history 수집 (CryContextRepository)
 *   2) 분류 (CryClassifier)
 *   3) 결과 저장 + 응답 합성
 *
 * history/getSample 은 confirmed-count 를 한 번만 조회해 LearningStage 를 계산하고
 * 모든 row 에 동일하게 적용한다 (이전 toResponseRow N+1 제거).
 */
@Service
class CryAnalysisService(
    private val repository: CryContextRepository,
    private val classifier: CryClassifier,
) {

    fun submit(babyId: String, request: SubmitCryRequest): CrySampleResponse {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val context = repository.buildContext(babyId, now)
        val features = FeatureVector(
            cryAvg = request.cryConfidenceAvg ?: 0.0,
            cryMax = request.cryConfidenceMax ?: 0.0,
            volAvg = request.avgVolumeDb ?: -50.0,
            volPeak = request.peakVolumeDb ?: -30.0,
            duration = request.durationSec,
            pitchMean = request.pitchMeanHz ?: 0.0,
            pitchStd = request.pitchStdHz ?: 0.0,
            zcrMean = request.zcrMean ?: 0.0,
            rhythmicity = request.rhythmicity ?: 0.0,
        )

        val confirmedHistory = repository.loadConfirmedHistory(babyId)
        val predictions = classifier.classify(features, context, confirmedHistory)
        val top = predictions.first()

        val id = UUID.randomUUID().toString()
        repository.saveSample(id, babyId, now, request, context, top)

        return CrySampleResponse(
            id = id,
            babyId = babyId,
            recordedAt = now.toString(),
            durationSec = request.durationSec,
            predictions = predictions,
            confirmedLabel = null,
            confirmedLabelDisplay = null,
            learningStage = LearningStage.from(confirmedHistory.size),
            note = request.note,
        )
    }

    fun confirm(sampleId: String, request: ConfirmCryRequest): CrySampleResponse {
        require(request.confirmedLabel in CryLabels.ALL || request.confirmedLabel == CryLabels.UNKNOWN) {
            "알 수 없는 라벨: ${request.confirmedLabel}"
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = repository.updateConfirmed(sampleId, request.confirmedLabel, request.note, now)
        require(updated > 0) { "울음 기록을 찾을 수 없어요" }
        return getSample(sampleId)
    }

    fun history(babyId: String, limit: Int = 50): List<CrySampleResponse> {
        val stage = LearningStage.from(repository.countConfirmed(babyId))
        return repository.findRecent(babyId, limit).map { it.copy(learningStage = stage) }
    }

    fun getSample(id: String): CrySampleResponse {
        val sample = repository.findById(id)
        val stage = LearningStage.from(repository.countConfirmed(sample.babyId))
        return sample.copy(learningStage = stage)
    }
}
