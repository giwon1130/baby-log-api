package com.giwon.babylog.features.feed

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
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

data class UpdateFeedRequest(
    val fedAt: String? = null,
    val amountMl: Int? = null,
    val feedType: String? = null,
    val note: String? = null,
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
            id, babyId, fedAt, request.amountMl, request.feedType, request.note,
        )
        return toResponse(id, babyId, fedAt, request.amountMl, request.feedType, request.note)
    }

    fun getFeeds(babyId: String, limit: Int = 20, date: String? = null): List<FeedResponse> {
        val (sql, params) = if (date != null) {
            val start = LocalDate.parse(date).atStartOfDay().atOffset(ZoneOffset.UTC)
            val end = LocalDate.parse(date).plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
            """select * from bl_feed_records where baby_id = ? and fed_at >= ? and fed_at < ?
               order by fed_at desc limit ?""" to arrayOf<Any>(babyId, start, end, limit)
        } else {
            """select * from bl_feed_records where baby_id = ?
               order by fed_at desc limit ?""" to arrayOf<Any>(babyId, limit)
        }
        return jdbc.query(sql, { rs, _ ->
            toResponse(
                id = rs.getString("id"),
                babyId = rs.getString("baby_id"),
                fedAt = rs.getObject("fed_at", OffsetDateTime::class.java),
                amountMl = rs.getInt("amount_ml"),
                feedType = rs.getString("feed_type"),
                note = rs.getString("note"),
            )
        }, *params)
    }

    fun getLatestFeed(babyId: String): FeedResponse? =
        runCatching {
            jdbc.queryForObject(
                "select * from bl_feed_records where baby_id = ? order by fed_at desc limit 1",
                { rs, _ ->
                    toResponse(
                        id = rs.getString("id"),
                        babyId = rs.getString("baby_id"),
                        fedAt = rs.getObject("fed_at", OffsetDateTime::class.java),
                        amountMl = rs.getInt("amount_ml"),
                        feedType = rs.getString("feed_type"),
                        note = rs.getString("note"),
                    )
                },
                babyId,
            )
        }.getOrNull()

    fun updateFeed(babyId: String, feedId: String, request: UpdateFeedRequest): FeedResponse {
        val fedAt = request.fedAt?.let { OffsetDateTime.parse(it) }
        val updateParts = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (request.amountMl != null) { updateParts += "amount_ml = ?"; params += request.amountMl }
        if (request.feedType != null) { updateParts += "feed_type = ?"; params += request.feedType }
        if (request.note != null) { updateParts += "note = ?"; params += request.note }
        if (fedAt != null) { updateParts += "fed_at = ?"; params += fedAt }

        if (updateParts.isNotEmpty()) {
            params += feedId; params += babyId
            jdbc.update(
                "update bl_feed_records set ${updateParts.joinToString(", ")} where id = ? and baby_id = ?",
                *params.toTypedArray(),
            )
        }
        return getFeed(babyId, feedId)
    }

    fun deleteFeed(babyId: String, feedId: String) {
        jdbc.update("delete from bl_feed_records where id = ? and baby_id = ?", feedId, babyId)
    }

    private fun getFeed(babyId: String, feedId: String): FeedResponse =
        jdbc.queryForObject(
            "select * from bl_feed_records where id = ? and baby_id = ?",
            { rs, _ ->
                toResponse(
                    id = rs.getString("id"),
                    babyId = rs.getString("baby_id"),
                    fedAt = rs.getObject("fed_at", OffsetDateTime::class.java),
                    amountMl = rs.getInt("amount_ml"),
                    feedType = rs.getString("feed_type"),
                    note = rs.getString("note"),
                )
            },
            feedId, babyId,
        ) ?: throw IllegalArgumentException("수유 기록을 찾을 수 없어.")

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

    private fun calculateNextFeedInterval(amountMl: Int): Double = when {
        amountMl <= 60 -> 2.0
        amountMl <= 90 -> 2.5
        amountMl <= 120 -> 3.0
        else -> 3.5
    }
}
