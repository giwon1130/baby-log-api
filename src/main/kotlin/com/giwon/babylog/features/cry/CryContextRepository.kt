package com.giwon.babylog.features.cry

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 울음 분석에 필요한 모든 DB 조회/저장. service 가 SQL 을 들고 다닐 필요 없게 모음.
 *
 * Row → CrySampleResponse 매핑도 여기서. service 는 learningStage 만 덧씌워 반환.
 */
@Repository
class CryContextRepository(private val jdbc: JdbcTemplate) {

    // ── Context for classifier ────────────────────────────────────────────────

    fun buildContext(babyId: String, now: OffsetDateTime): ContextSnapshot {
        val sinceFeed = minutesSince(latestFeedAt(babyId), now)
        val sinceDiaper = minutesSince(latestDiaperAt(babyId), now)
        val lastSleep = latestSleep(babyId)
        val sinceSleepStart = minutesSince(lastSleep?.first, now)
        val sinceSleepEnd = minutesSince(lastSleep?.second, now)
        val isDuringSleep = lastSleep != null && lastSleep.first != null && lastSleep.second == null

        val birthDate = birthDateOf(babyId)
        val ageDays = birthDate?.let {
            Duration.between(it.atStartOfDay(ZoneOffset.UTC).toInstant(), now.toInstant()).toDays().toInt()
        }

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

    private fun latestFeedAt(babyId: String): OffsetDateTime? = jdbc.query(
        "select fed_at from bl_feed_records where baby_id = ? order by fed_at desc limit 1",
        { rs, _ -> rs.getObject("fed_at", OffsetDateTime::class.java) }, babyId,
    ).firstOrNull()

    private fun latestDiaperAt(babyId: String): OffsetDateTime? = jdbc.query(
        "select changed_at from bl_diaper_records where baby_id = ? order by changed_at desc limit 1",
        { rs, _ -> rs.getObject("changed_at", OffsetDateTime::class.java) }, babyId,
    ).firstOrNull()

    private fun latestSleep(babyId: String): Pair<OffsetDateTime?, OffsetDateTime?>? = jdbc.query(
        "select slept_at, woke_at from bl_sleep_records where baby_id = ? order by slept_at desc limit 1",
        { rs, _ ->
            Pair(
                rs.getObject("slept_at", OffsetDateTime::class.java),
                rs.getObject("woke_at", OffsetDateTime::class.java),
            )
        }, babyId,
    ).firstOrNull()

    private fun birthDateOf(babyId: String): LocalDate? = jdbc.query(
        "select birth_date from bl_babies where id = ?",
        { rs, _ -> rs.getObject("birth_date", LocalDate::class.java) }, babyId,
    ).firstOrNull()

    private fun minutesSince(ts: OffsetDateTime?, now: OffsetDateTime): Int? =
        ts?.let { Duration.between(it, now).toMinutes().toInt().coerceAtLeast(0) }

    // ── Confirmed history (for classifier similarity boost) ──────────────────

    fun loadConfirmedHistory(babyId: String): List<ConfirmedSample> = jdbc.query(
        """select confirmed_label, duration_sec, cry_confidence_avg, cry_confidence_max,
                  avg_volume_db, peak_volume_db,
                  pitch_mean_hz, pitch_std_hz, zcr_mean, rhythmicity
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
                    pitchMean = rs.getObject("pitch_mean_hz") as? Double ?: 0.0,
                    pitchStd = rs.getObject("pitch_std_hz") as? Double ?: 0.0,
                    zcrMean = rs.getObject("zcr_mean") as? Double ?: 0.0,
                    rhythmicity = rs.getObject("rhythmicity") as? Double ?: 0.0,
                ),
            )
        },
        babyId,
    )

    fun countConfirmed(babyId: String): Int = jdbc.queryForObject(
        "select count(*) from bl_cry_samples where baby_id = ? and confirmed_label is not null",
        Int::class.java,
        babyId,
    )

    // ── Sample writes ─────────────────────────────────────────────────────────

    fun saveSample(
        id: String,
        babyId: String,
        now: OffsetDateTime,
        request: SubmitCryRequest,
        context: ContextSnapshot,
        topPrediction: CryPrediction,
    ) {
        jdbc.update(
            """
            insert into bl_cry_samples (
                id, baby_id, recorded_at, duration_sec,
                cry_confidence_avg, cry_confidence_max, avg_volume_db, peak_volume_db,
                pitch_mean_hz, pitch_std_hz, pitch_max_hz, voiced_ratio, zcr_mean, rhythmicity,
                minutes_since_last_feed, minutes_since_last_diaper,
                minutes_since_last_sleep_start, minutes_since_last_sleep_end,
                is_during_sleep, baby_age_days, time_of_day_hour,
                predicted_label, predicted_confidence, note
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, babyId, now, request.durationSec,
            request.cryConfidenceAvg, request.cryConfidenceMax,
            request.avgVolumeDb, request.peakVolumeDb,
            request.pitchMeanHz, request.pitchStdHz, request.pitchMaxHz,
            request.voicedRatio, request.zcrMean, request.rhythmicity,
            context.minutesSinceFeed, context.minutesSinceDiaper,
            context.minutesSinceSleepStart, context.minutesSinceSleepEnd,
            context.isDuringSleep, context.babyAgeDays, context.timeOfDayHour,
            topPrediction.label, topPrediction.confidence, request.note,
        )
    }

    fun updateConfirmed(sampleId: String, label: String, note: String, now: OffsetDateTime): Int = jdbc.update(
        """update bl_cry_samples
           set confirmed_label = ?, confirmed_at = ?, note = case when ? = '' then note else ? end
           where id = ?""".trimIndent(),
        label, now, note, note, sampleId,
    )

    // ── Sample reads (RowMapper) ──────────────────────────────────────────────
    //
    // 반환되는 응답의 learningStage 는 caller (service) 가 한 번만 계산해서 .copy 로
    // 덮어쓰는 게 N+1 회피 방식. 여기선 placeholder 로 LearningStage.from(0) 사용.

    fun findRecent(babyId: String, limit: Int): List<CrySampleResponse> = jdbc.query(
        """select * from bl_cry_samples where baby_id = ?
           order by recorded_at desc limit ?""".trimIndent(),
        { rs, _ -> mapRow(rs) },
        babyId, limit,
    )

    fun findById(id: String): CrySampleResponse = jdbc.queryForObject(
        "select * from bl_cry_samples where id = ?",
        { rs, _ -> mapRow(rs) },
        id,
    ) ?: throw IllegalArgumentException("울음 기록을 찾을 수 없어요")

    private fun mapRow(rs: ResultSet): CrySampleResponse {
        val confirmed = rs.getString("confirmed_label")
        val predicted = rs.getString("predicted_label")
        return CrySampleResponse(
            id = rs.getString("id"),
            babyId = rs.getString("baby_id"),
            recordedAt = rs.getObject("recorded_at", OffsetDateTime::class.java).toString(),
            durationSec = rs.getDouble("duration_sec"),
            predictions = listOf(
                CryPrediction(
                    label = predicted,
                    labelDisplay = CryLabels.DISPLAY[predicted] ?: predicted,
                    confidence = rs.getDouble("predicted_confidence"),
                    reasons = emptyList(),
                ),
            ),
            confirmedLabel = confirmed,
            confirmedLabelDisplay = confirmed?.let { CryLabels.DISPLAY[it] ?: it },
            learningStage = LearningStage.from(0),  // placeholder — service 가 덮어씀
            note = rs.getString("note") ?: "",
        )
    }
}
