package ru.sablebot.api.controller

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.sablebot.api.dto.feed.CreateFeedRequest
import ru.sablebot.api.dto.feed.FeedListResponse
import ru.sablebot.api.dto.feed.FeedResponse
import ru.sablebot.api.dto.feed.UpdateFeedRequest
import ru.sablebot.api.security.annotation.RequireGuildPermission
import ru.sablebot.api.service.FeedApiService

@RestController
@RequestMapping("/api/guilds/{guildId}/feeds")
class FeedController(
    private val feedApiService: FeedApiService
) {

    @GetMapping
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getFeeds(@PathVariable guildId: String): ResponseEntity<FeedListResponse> {
        return ResponseEntity.ok(feedApiService.getFeeds(guildId.toLong()))
    }

    @PostMapping
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun createFeed(
        @PathVariable guildId: String,
        @Valid @RequestBody request: CreateFeedRequest
    ): ResponseEntity<FeedResponse> {
        return ResponseEntity.ok(feedApiService.createFeed(guildId.toLong(), request))
    }

    @PatchMapping("/{feedId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun updateFeed(
        @PathVariable guildId: String,
        @PathVariable feedId: String,
        @Valid @RequestBody request: UpdateFeedRequest
    ): ResponseEntity<FeedResponse> {
        return ResponseEntity.ok(feedApiService.updateFeed(feedId.toLong(), request))
    }

    @DeleteMapping("/{feedId}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun deleteFeed(
        @PathVariable guildId: String,
        @PathVariable feedId: String
    ): ResponseEntity<Void> {
        feedApiService.deleteFeed(feedId.toLong())
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{feedId}/test")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun testFeed(
        @PathVariable guildId: String,
        @PathVariable feedId: String
    ): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(feedApiService.testFeed(feedId.toLong()))
    }
}
