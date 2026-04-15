package com.giwon.babylog.features.shortcuts

import com.giwon.babylog.features.diaper.CreateDiaperRequest
import com.giwon.babylog.features.diaper.DiaperService
import com.giwon.babylog.features.feed.CreateFeedRequest
import com.giwon.babylog.features.feed.FeedService
import com.giwon.babylog.features.sleep.SleepService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

data class ShortcutFeedRequest(
    val inviteCode: String,
    val babyName: String? = null,
    val amountMl: Int? = null,
    val feedType: String = "FORMULA",
)

data class ShortcutDiaperRequest(
    val inviteCode: String,
    val babyName: String? = null,
    val diaperType: String = "WET",
)

data class ShortcutSleepRequest(
    val inviteCode: String,
    val babyName: String? = null,
)

data class ShortcutResult(val message: String)

@Service
class ShortcutsService(
    private val jdbc: JdbcTemplate,
    private val feedService: FeedService,
    private val diaperService: DiaperService,
    private val sleepService: SleepService,
) {

    fun recordFeed(req: ShortcutFeedRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val babyName = req.babyName ?: getBabyName(babyId)
        feedService.recordFeed(
            babyId, CreateFeedRequest(
                amountMl = req.amountMl ?: 0,
                feedType = req.feedType,
            )
        )
        val amountText = if ((req.amountMl ?: 0) > 0) " ${req.amountMl}ml" else ""
        return ShortcutResult("$babyName 수유${amountText} 기록됐어요 ✅")
    }

    fun recordDiaper(req: ShortcutDiaperRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val babyName = req.babyName ?: getBabyName(babyId)
        diaperService.recordDiaper(
            babyId, CreateDiaperRequest(diaperType = req.diaperType)
        )
        val typeLabel = when (req.diaperType) {
            "WET" -> "소변"; "DIRTY" -> "대변"; "MIXED" -> "대소변"; else -> req.diaperType
        }
        return ShortcutResult("$babyName 기저귀($typeLabel) 교환 기록됐어요 ✅")
    }

    fun startSleep(req: ShortcutSleepRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val babyName = req.babyName ?: getBabyName(babyId)
        sleepService.startSleep(babyId, com.giwon.babylog.features.sleep.StartSleepRequest())
        return ShortcutResult("$babyName 수면 시작 기록됐어요 😴")
    }

    fun endSleep(req: ShortcutSleepRequest): ShortcutResult {
        val babyId = resolveBabyId(req.inviteCode, req.babyName)
        val babyName = req.babyName ?: getBabyName(babyId)
        val activeSleep = sleepService.getActiveSleep(babyId)
            ?: return ShortcutResult("$babyName 진행 중인 수면 기록이 없어요")
        sleepService.endSleep(babyId, activeSleep.id, com.giwon.babylog.features.sleep.EndSleepRequest())
        return ShortcutResult("$babyName 수면 종료 기록됐어요 ☀️")
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
