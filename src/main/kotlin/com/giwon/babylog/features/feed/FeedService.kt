package com.giwon.babylog.features.feed

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class FeedResponse(
    val id: String,
    val babyId: String,
    val fedAt: String,
    val amountMl: Int,
    val feedType: String,
    val note: String,
    val nextFeedAt: String,
    val nextFeedIntervalHours: Double,
)

data class CreateFeedRequest(
    val fedAt: String? = null,
    val amountMl: Int,
    val feedType: String = "FORMULA",
    val note: String = "",
)

@Service
class FeedService(private val jdbc: JdbcTemplate) {

    fun recordFeed(babyId: String, request: CreateFeedRequest): FeedResponse {
        val id = UUID.randomUUID().toString()
        val fedAt = request.fedAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC)

        jdbc.update(
            """insert into bl_feed_records (id, baby_id, fed_at, amount_ml, feed_type, note)
               values (?, ?, ?, ?, ?, ?)""",
            id, babyId, fedAt.toString(), request.amountMl, request.feedType, request.note,
        )
        return toResponse(id, babyId, fedAt, request.amountMl, request.feedType, request.note)
    }

    fun getFeeds(babyId: String, limit: Int = 20): List<FeedResponse> =
        jdbc.query(
            """select * from bl_feed_records where baby_id = ?
               order by fed_at desc limit ?""",
            { rs, _ ->
                toResponse(
                    id = rs.getString("id"),
                    babyId = rs.getString("baby_id"),
                    fedAt = OffsetDateTime.parse(rs.getString("fed_at")),
                    amountMl = rs.getInt("amount_ml"),
                    feedType = rs.getString("feed_type"),
                    note = rs.getString("note"),
                )
            },
            babyId, limit,
        )

    fun getLatestFeed(babyId: String): FeedResponse? =
        runCatching {
            jdbc.queryForObject(
                "select * from bl_feed_records where baby_id = ? order by fed_at desc limit 1",
                { rs, _ ->
                    toResponse(
                        id = rs.getString("id"),
                        babyId = rs.getString("baby_id"),
                        fedAt = OffsetDateTime.parse(rs.getString("fed_at")),
                        amountMl = rs.getInt("amount_ml"),
                        feedType = rs.getString("feed_type"),
                        note = rs.getString("note"),
                    )
                },
                babyId,
            )
        }.getOrNull()

    private fun toResponse(
        id: String,
        babyId: String,
        fedAt: OffsetDateTime,
        amountMl: Int,
        feedType: String,
        note: String,
    ): FeedResponse {
        val intervalHours = calculateNextFeedInterval(amountMl)
        val nextFeedAt = fedAt.plusMinutes((intervalHours * 60).toLong())
        return FeedResponse(
            id = id,
            babyId = babyId,
            fedAt = fedAt.toString(),
            amountMl = amountMl,
            feedType = feedType,
            note = note,
            nextFeedAt = nextFeedAt.toString(),
            nextFeedIntervalHours = intervalHours,
        )
    }

    /**
     * 분유량에 따른 다음 수유 간격 (신생아 기준)
     * - 60ml 이하: 2시간
     * - 60~90ml: 2.5시간
     * - 90~120ml: 3시간
     * - 120ml 이상: 3.5시간
     */
    private fun calculateNextFeedInterval(amountMl: Int): Double = when {
        amountMl <= 60 -> 2.0
        amountMl <= 90 -> 2.5
        amountMl <= 120 -> 3.0
        else -> 3.5
    }
}
