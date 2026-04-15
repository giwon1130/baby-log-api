package com.giwon.babylog.features.stats

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset

data class DailyFeedStat(val date: String, val feedCount: Int, val totalMl: Int)
data class DailySleepStat(val date: String, val sleepCount: Int, val totalMinutes: Long)

data class WeeklyStatsResponse(
    val feedStats: List<DailyFeedStat>,
    val sleepStats: List<DailySleepStat>,
)

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

    fun getWeeklyStats(babyId: String): WeeklyStatsResponse {
        val today = LocalDate.now(ZoneOffset.UTC)
        val weekStart = today.minusDays(6)
        val start = weekStart.atStartOfDay().atOffset(ZoneOffset.UTC)
        val end = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }

        // Single GROUP BY query instead of 7 individual queries
        val feedByDay = jdbc.query(
            """select date(fed_at at time zone 'UTC') as day,
                      count(*)::int as cnt,
                      coalesce(sum(amount_ml), 0)::int as total_ml
               from bl_feed_records
               where baby_id = ? and fed_at >= ? and fed_at < ?
               group by 1""",
            { rs, _ -> rs.getString("day") to DailyFeedStat(
                date = rs.getString("day"),
                feedCount = rs.getInt("cnt"),
                totalMl = rs.getInt("total_ml"),
            )},
            babyId, start, end,
        ).toMap()

        val sleepByDay = jdbc.query(
            """select date(slept_at at time zone 'UTC') as day,
                      count(*)::int as cnt,
                      coalesce(sum(
                        extract(epoch from (coalesce(woke_at, now()) - slept_at)) / 60
                      ), 0)::bigint as total_min
               from bl_sleep_records
               where baby_id = ? and slept_at >= ? and slept_at < ?
               group by 1""",
            { rs, _ -> rs.getString("day") to DailySleepStat(
                date = rs.getString("day"),
                sleepCount = rs.getInt("cnt"),
                totalMinutes = rs.getLong("total_min"),
            )},
            babyId, start, end,
        ).toMap()

        val feedStats = days.map { day ->
            feedByDay[day.toString()] ?: DailyFeedStat(date = day.toString(), feedCount = 0, totalMl = 0)
        }
        val sleepStats = days.map { day ->
            sleepByDay[day.toString()] ?: DailySleepStat(date = day.toString(), sleepCount = 0, totalMinutes = 0)
        }

        return WeeklyStatsResponse(feedStats = feedStats, sleepStats = sleepStats)
    }

    fun getTodayStats(babyId: String): TodayStatsResponse {
        val today = LocalDate.now(ZoneOffset.UTC)
        val startOfDay = today.atStartOfDay().atOffset(ZoneOffset.UTC)
        val endOfDay = today.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)

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
