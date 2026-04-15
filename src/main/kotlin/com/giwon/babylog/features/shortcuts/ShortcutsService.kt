package com.giwon.babylog.features.shortcuts

import com.giwon.babylog.features.diaper.CreateDiaperRequest
import com.giwon.babylog.features.diaper.DiaperService
import com.giwon.babylog.features.feed.CreateFeedRequest
import com.giwon.babylog.features.feed.FeedService
import com.giwon.babylog.features.growth.CreateGrowthRecordRequest
import com.giwon.babylog.features.growth.GrowthRecordService
import com.giwon.babylog.features.health.CreateHealthRecordRequest
import com.giwon.babylog.features.health.HealthRecordService
import com.giwon.babylog.features.sleep.EndSleepRequest
import com.giwon.babylog.features.sleep.SleepService
import com.giwon.babylog.features.sleep.StartSleepRequest
import com.giwon.babylog.features.stats.StatsService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Request DTOs ─────────────────────────────────────────────────────────────

data class ShortcutFeedRequest(
    val inviteCode: String,
    val babyName: String? = null,
    val amountMl: Int? = null,
    val feedType: String = "FORMULA",          // FORMULA | BREAST | MIXED
    val leftMinutes: Double? = null,
    val rightMinutes: Double? = null,
)

data class ShortcutDiaperRequest(
    val inviteCode: String,
    val babyName: String? = null,
    val diaperType: String = "WET",             // WET | DIRTY | MIXED | DRY
)

data class ShortcutSleepRequest(
    val inviteCode: String,
    val babyName: String? = null,
)

data class ShortcutTemperatureRequest(
    val inviteCode: String,
    val babyName: String? = null,
    val temperature: Double,                   // °C, e.g. 38.5
    val note: String = "",
)

data class ShortcutMedicineRequest(
    val inviteCode: String,
    val babyName: String? = null,
    val medicineName: String,                  // e.g. "타이레놀"
    val dosage: Double? = null,                // e.g. 2.5
    val note: String = "",
)

data class ShortcutGrowthRequest(
    val inviteCode: String,
    val babyName: String? = null,
    val weightG: Int? = null,                  // grams
    val heightCm: Double? = null,
    val headCm: Double? = null,
    val note: String = "",
)

data class ShortcutStatusRequest(
    val inviteCode: String,
    val babyName: String? = null,
)

data class ShortcutResult(val message: String)

// ── Service ──────────────────────────────────────────────────────────────────

@Service
class ShortcutsService(
    private val jdbc: JdbcTemplate,
    private val feedService: FeedService,
    private val diaperService: DiaperService,
    private val sleepService: SleepService,
    private val healthService: HealthRecordService,
    private val growthService: GrowthRecordService,
    private val statsService: StatsService,
) {

    // ── 기록형 ──────────────────────────────────────────────────────────────

    fun recordFeed(req: ShortcutFeedRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)
        feedService.recordFeed(
            babyId, CreateFeedRequest(
                amountMl = req.amountMl ?: 0,
                feedType = req.feedType,
                leftMinutes = req.leftMinutes,
                rightMinutes = req.rightMinutes,
            )
        )
        val detail = when {
            req.feedType == "BREAST" || req.feedType == "MIXED" -> buildString {
                req.leftMinutes?.let { append("왼쪽 ${it.toInt()}분") }
                req.rightMinutes?.let {
                    if (isNotEmpty()) append(" · ")
                    append("오른쪽 ${it.toInt()}분")
                }
            }.ifEmpty { "모유 수유" }
            (req.amountMl ?: 0) > 0 -> "${req.amountMl}ml"
            else -> "수유"
        }
        return ShortcutResult("$name $detail 기록됐어요 ✅")
    }

    fun recordDiaper(req: ShortcutDiaperRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)
        diaperService.recordDiaper(babyId, CreateDiaperRequest(diaperType = req.diaperType))
        val label = when (req.diaperType) {
            "WET" -> "소변"; "DIRTY" -> "대변"; "MIXED" -> "대소변"; "DRY" -> "건조"; else -> req.diaperType
        }
        return ShortcutResult("$name 기저귀($label) 교환 기록됐어요 ✅")
    }

    fun startSleep(req: ShortcutSleepRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)
        sleepService.startSleep(babyId, StartSleepRequest())
        return ShortcutResult("$name 수면 시작 기록됐어요 😴")
    }

    fun endSleep(req: ShortcutSleepRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)
        val active = sleepService.getActiveSleep(babyId)
            ?: return ShortcutResult("$name 진행 중인 수면 기록이 없어요")
        val ended = sleepService.endSleep(babyId, active.id, EndSleepRequest())
        val dur = ended.durationMinutes?.let { "${it / 60}시간 ${it % 60}분" } ?: ""
        return ShortcutResult("$name 수면 종료 기록됐어요 ☀️${if (dur.isNotEmpty()) " ($dur)" else ""}")
    }

    fun recordTemperature(req: ShortcutTemperatureRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)
        healthService.recordHealth(
            babyId, CreateHealthRecordRequest(
                type = "TEMPERATURE",
                value = req.temperature,
                note = req.note,
            )
        )
        val feverNote = when {
            req.temperature >= 38.5 -> " 🔴 발열"
            req.temperature >= 38.0 -> " 🟡 미열"
            else -> ""
        }
        return ShortcutResult("$name 체온 ${req.temperature}°C 기록됐어요$feverNote ✅")
    }

    fun recordMedicine(req: ShortcutMedicineRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)
        healthService.recordHealth(
            babyId, CreateHealthRecordRequest(
                type = "MEDICINE",
                name = req.medicineName,
                value = req.dosage,
                note = req.note,
            )
        )
        val dosageText = req.dosage?.let { " ${it}ml" } ?: ""
        return ShortcutResult("$name ${req.medicineName}${dosageText} 투여 기록됐어요 ✅")
    }

    fun recordGrowth(req: ShortcutGrowthRequest): ShortcutResult {
        if (req.weightG == null && req.heightCm == null && req.headCm == null)
            throw IllegalArgumentException("체중, 키, 머리 둘레 중 하나 이상 입력해주세요")
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)
        growthService.recordGrowth(
            babyId, CreateGrowthRecordRequest(
                weightG = req.weightG,
                heightCm = req.heightCm,
                headCm = req.headCm,
                note = req.note,
            )
        )
        val parts = listOfNotNull(
            req.weightG?.let { "${it / 1000.0}kg" },
            req.heightCm?.let { "${it}cm" },
            req.headCm?.let { "머리 ${it}cm" },
        )
        return ShortcutResult("$name 성장 기록됐어요 (${parts.joinToString(", ")}) 📏")
    }

    // ── 조회형 ──────────────────────────────────────────────────────────────

    fun getStatus(req: ShortcutStatusRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)

        val latestFeed = feedService.getLatestFeed(babyId)
        val activeSleep = sleepService.getActiveSleep(babyId)
        val stats = runCatching { statsService.getTodayStats(babyId) }.getOrNull()

        val kst = ZoneId.of("Asia/Seoul")
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        val lines = mutableListOf<String>()

        // 수면 상태
        if (activeSleep != null) {
            val sleptAt = OffsetDateTime.parse(activeSleep.sleptAt).atZoneSameInstant(kst)
            lines += "😴 지금 자는 중 (${sleptAt.format(timeFmt)}부터)"
        }

        // 최근 수유
        if (latestFeed != null) {
            val fedAt = OffsetDateTime.parse(latestFeed.fedAt).atZoneSameInstant(kst)
            val elapsed = (System.currentTimeMillis() - fedAt.toInstant().toEpochMilli()) / 60000
            val h = elapsed / 60; val m = elapsed % 60
            val elapsedText = if (h > 0) "${h}시간 ${m}분 전" else "${m}분 전"
            lines += "🍼 마지막 수유: ${fedAt.format(timeFmt)} ($elapsedText)"
            latestFeed.nextFeedAt?.let { next ->
                val nextTime = OffsetDateTime.parse(next).atZoneSameInstant(kst)
                val until = (nextTime.toInstant().toEpochMilli() - System.currentTimeMillis()) / 60000
                if (until > 0) lines += "   ⏰ 다음 수유까지 ${until / 60}시간 ${until % 60}분"
                else lines += "   ⏰ 수유 시간 됐어요!"
            }
        }

        // 오늘 요약
        stats?.let {
            lines += "📊 오늘: 수유 ${it.feedCount}회 ${it.totalFeedMl}ml · 기저귀 ${it.diaperCount}회"
        }

        val message = if (lines.isEmpty()) "$name 오늘 기록이 없어요" else "$name 현재 상태\n${lines.joinToString("\n")}"
        return ShortcutResult(message)
    }

    fun getTodaySummary(req: ShortcutStatusRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val name = req.babyName ?: getBabyName(babyId)
        val stats = statsService.getTodayStats(babyId)
        val sleepH = stats.totalSleepMinutes / 60
        val sleepM = stats.totalSleepMinutes % 60
        val message = """
            $name 오늘 요약 📋
            🍼 수유 ${stats.feedCount}회 · ${stats.totalFeedMl}ml
            🧷 기저귀 ${stats.diaperCount}회 (소변 ${stats.wetCount} · 대변 ${stats.dirtyCount})
            😴 수면 ${stats.sleepCount}회 · ${sleepH}시간 ${sleepM}분
        """.trimIndent()
        return ShortcutResult(message)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun resolveBabyId(inviteCode: String, babyName: String?): String {
        val familyId = runCatching {
            jdbc.queryForObject(
                "select id from bl_families where invite_code = ?",
                String::class.java,
                inviteCode,
            )
        }.getOrNull() ?: throw IllegalArgumentException("초대 코드를 찾을 수 없어요: $inviteCode")

        val babies = jdbc.query(
            "select id, name from bl_babies where family_id = ? order by birth_date",
            { rs, _ -> Pair(rs.getString("id"), rs.getString("name")) },
            familyId,
        )
        if (babies.isEmpty()) throw IllegalArgumentException("등록된 아기가 없어요")

        return if (babyName != null) {
            babies.firstOrNull { it.second == babyName }?.first
                ?: throw IllegalArgumentException("'$babyName' 이름의 아기를 찾을 수 없어요")
        } else {
            babies.first().first
        }
    }

    private fun getBabyName(babyId: String): String =
        runCatching {
            jdbc.queryForObject(
                "select name from bl_babies where id = ?",
                String::class.java,
                babyId,
            )
        }.getOrNull() ?: "아기"
}
