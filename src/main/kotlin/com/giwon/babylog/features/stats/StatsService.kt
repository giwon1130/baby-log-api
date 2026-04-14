package com.giwon.babylog.features.stats

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset

data class TodayStatsResponse(
    val date: String,
    val feedCount: Int,
    val totalFeedMl: Int,
    val diaperCount: Int,
    val wetCount: Int,
    val dirtyCount: Int,
    val sleepCount: Int,
    val totalSleepMinutes: Long,
)

@Service
class StatsService(private val jdbc: JdbcTemplate) {

    fun getTodayStats(babyId: String): TodayStatsResponse {
        val today = LocalDate.now(ZoneOffset.UTC)
        val startOfDay = today.atStartOfDay(ZoneOffset.UTC).toString()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toString()

        val feedStats = jdbc.queryForMap(
            """select count(*) as cnt, coalesce(sum(amount_ml), 0) as total_ml
               from bl_feed_records
               where baby_id = ? and fed_at >= ? and fed_at < ?""",
            babyId, startOfDay, endOfDay,
        )

        val diaperStats = jdbc.queryForMap(
            """select
                 count(*) as cnt,
                 count(*) filter (where diaper_type = 'WET') as wet,
                 count(*) filter (where diaper_type in ('DIRTY', 'MIXED')) as dirty
               from bl_diaper_records
               where baby_id = ? and changed_at >= ? and changed_at < ?""",
            babyId, startOfDay, endOfDay,
        )

        val sleepStats = jdbc.queryForMap(
            """select
                 count(*) as cnt,
                 coalesce(sum(
                   extract(epoch from (coalesce(woke_at::timestamptz, now()) - slept_at::timestamptz)) / 60
                 ), 0) as total_minutes
               from bl_sleep_records
               where baby_id = ? and slept_at >= ? and slept_at < ?""",
            babyId, startOfDay, endOfDay,
        )

        return TodayStatsResponse(
            date = today.toString(),
            feedCount = (feedStats["cnt"] as Number).toInt(),
            totalFeedMl = (feedStats["total_ml"] as Number).toInt(),
            diaperCount = (diaperStats["cnt"] as Number).toInt(),
            wetCount = (diaperStats["wet"] as Number).toInt(),
            dirtyCount = (diaperStats["dirty"] as Number).toInt(),
            sleepCount = (sleepStats["cnt"] as Number).toInt(),
            totalSleepMinutes = (sleepStats["total_minutes"] as Number).toLong(),
        )
    }
}
