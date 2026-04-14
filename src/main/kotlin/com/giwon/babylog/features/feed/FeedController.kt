package com.giwon.babylog.features.feed

import com.giwon.babylog.common.ApiResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/babies/{babyId}/feeds")
class FeedController(private val feedService: FeedService) {

    @PostMapping
    fun recordFeed(
        @PathVariable babyId: String,
        @RequestBody request: CreateFeedRequest,
    ): ApiResponse<FeedResponse> = ApiResponse.ok(feedService.recordFeed(babyId, request))

    @GetMapping
    fun getFeeds(
        @PathVariable babyId: String,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) date: String?,
    ): ApiResponse<List<FeedResponse>> = ApiResponse.ok(feedService.getFeeds(babyId, limit, date))

    @GetMapping("/latest")
    fun getLatestFeed(@PathVariable babyId: String): ApiResponse<FeedResponse?> =
        ApiResponse.ok(feedService.getLatestFeed(babyId))

    @PutMapping("/{feedId}")
    fun updateFeed(
        @PathVariable babyId: String,
        @PathVariable feedId: String,
        @RequestBody request: UpdateFeedRequest,
    ): ApiResponse<FeedResponse> = ApiResponse.ok(feedService.updateFeed(babyId, feedId, request))

    @DeleteMapping("/{feedId}")
    fun deleteFeed(
        @PathVariable babyId: String,
        @PathVariable feedId: String,
    ): ApiResponse<Unit> {
        feedService.deleteFeed(babyId, feedId)
        return ApiResponse.ok(Unit)
    }
}
