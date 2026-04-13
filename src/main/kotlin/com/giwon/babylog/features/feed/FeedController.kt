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
        @RequestParam(defaultValue = "20") limit: Int,
    ): ApiResponse<List<FeedResponse>> = ApiResponse.ok(feedService.getFeeds(babyId, limit))

    @GetMapping("/latest")
    fun getLatestFeed(@PathVariable babyId: String): ApiResponse<FeedResponse?> =
        ApiResponse.ok(feedService.getLatestFeed(babyId))
}
