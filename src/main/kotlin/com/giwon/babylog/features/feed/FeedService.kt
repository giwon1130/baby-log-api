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
    val leftMinutes: Double? = null,
    val rightMinutes: Double? = null,
)

data class CreateFeedRequest(
    val fedAt: String? = null,
    val amountMl: Int = 0,
    val feedType: String = "FORMULA",
    val note: String = "",
    val leftMinutes: Double? = null,
    val rightMinutes: Double? = null,
)

data class UpdateFeedRequest(
    val fedAt: String? = null,
    val amountMl: Int? = null,
    val feedType: String? = null,
    val note: String? = null,
    val leftMinutes: Double? = null,
    val rightMinutes: Double? = null,
)

@Service
class FeedService(private val jdbc: JdbcTemplate) {

    fun recordFeed(babyId: String, request: CreateFeedRequest): FeedResponse {
        val id = UUID.randomUUID().toString()
        val fedAt = request.fedAt?.let { OffsetDateTime.parse(it) }
            ?: OffsetDateTime.now(ZoneOffset.UTC)

        jdbc.update(
            """insert into bl_feed_records (id, baby_id, fed_at, amount_ml, feed_type, note, left_minutes, right_minutes)
               values (?, ?, ?, ?, ?, ?, ?, ?)""",
            id, babyId, fedAt, request.amountMl, request.feedType, request.note,
            request.leftMinutes, request.rightMinutes,
        )
        return toResponse(id, babyId, fedAt, request.amountMl, request.feedType, request.note,
            request.leftMinutes, request.rightMinutes)
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
        return jdbc.query(sql, { rs, _ -> rs.toFeedResponse() }, *params)
    }

    fun getLatestFeed(babyId: String): FeedResponse? =
        runCatching {
            jdbc.queryForObject(
                "select * from bl_feed_records where baby_id = ? order by fed_at desc limit 1",
                { rs, _ -> rs.toFeedResponse() },
                babyId,
            )
        }.getOrNull()

    fun updateFeed(babyId: String, feedId: String, request: UpdateFeedRequest): FeedResponse {
        val fedAt = request.fedAt?.let { OffsetDateTime.parse(it) }
        val updateParts = mutableListOf<String>()
        val params = mutableListOf<Any?>()

        if (request.amountMl != null) { updateParts += "amount_ml = ?"; params += request.amountMl }
        if (request.feedType != null) { updateParts += "feed_type = ?"; params += request.feedType }
        if (request.note != null) { updateParts += "note = ?"; params += request.note }
        if (fedAt != null) { updateParts += "fed_at = ?"; params += fedAt }
        if (request.leftMinutes != null) { updateParts += "left_minutes = ?"; params += request.leftMinutes }
        if (request.rightMinutes != null) { updateParts += "right_minutes = ?"; params += request.rightMinutes }

        if (updateParts.isEmpty()) return getFeed(babyId, feedId)

        params += feedId; params += babyId
        return jdbc.query(
            "update bl_feed_records set ${updateParts.joinToString(", ")} where id = ? and baby_id = ? returning *",
            { rs, _ -> rs.toFeedResponse() },
            *params.toTypedArray(),
        ).firstOrNull() ?: throw IllegalArgumentException("수유 기록을 찾을 수 없어.")
    }

    fun deleteFeed(babyId: String, feedId: String) {
        jdbc.update("delete from bl_feed_records where id = ? and baby_id = ?", feedId, babyId)
    }

    private fun getFeed(babyId: String, feedId: String): FeedResponse =
        jdbc.queryForObject(
            "select * from bl_feed_records where id = ? and baby_id = ?",
            { rs, _ -> rs.toFeedResponse() },
            feedId, babyId,
        ) ?: throw IllegalArgumentException("수유 기록을 찾을 수 없어.")

    private fun java.sql.ResultSet.toFeedResponse(): FeedResponse {
        val fedAt = getObject("fed_at", OffsetDateTime::class.java)
        val amountMl = getInt("amount_ml")
        val feedType = getString("feed_type")
        val leftMinutes = getObject("left_minutes") as? Double
        val rightMinutes = getObject("right_minutes") as? Double
        val intervalHours = calculateNextFeedInterval(amountMl, feedType, leftMinutes, rightMinutes)
        return FeedResponse(
            id = getString("id"),
            babyId = getString("baby_id"),
            fedAt = fedAt.toString(),
            amountMl = amountMl,
            feedType = feedType,
            note = getString("note"),
            nextFeedAt = fedAt.plusMinutes((intervalHours * 60).toLong()).toString(),
            nextFeedIntervalHours = intervalHours,
            leftMinutes = leftMinutes,
            rightMinutes = rightMinutes,
        )
    }

    private fun toResponse(
        id: String, babyId: String, fedAt: OffsetDateTime,
        amountMl: Int, feedType: String, note: String,
        leftMinutes: Double? = null, rightMinutes: Double? = null,
    ): FeedResponse {
        val intervalHours = calculateNextFeedInterval(amountMl, feedType, leftMinutes, rightMinutes)
        return FeedResponse(
            id = id, babyId = babyId, fedAt = fedAt.toString(),
            amountMl = amountMl, feedType = feedType, note = note,
            nextFeedAt = fedAt.plusMinutes((intervalHours * 60).toLong()).toString(),
            nextFeedIntervalHours = intervalHours,
            leftMinutes = leftMinutes, rightMinutes = rightMinutes,
        )
    }

    internal fun calculateNextFeedInterval(
        amountMl: Int, feedType: String = "FORMULA",
        leftMinutes: Double? = null, rightMinutes: Double? = null,
    ): Double {
        if (feedType == "BREAST" || feedType == "MIXED") {
            val totalMin = (leftMinutes ?: 0.0) + (rightMinutes ?: 0.0)
            return if (totalMin >= 15) 2.5 else 2.0
        }
        return when {
            amountMl <= 60 -> 2.0
            amountMl <= 90 -> 2.5
            amountMl <= 120 -> 3.0
            else -> 3.5
        }
    }
}
