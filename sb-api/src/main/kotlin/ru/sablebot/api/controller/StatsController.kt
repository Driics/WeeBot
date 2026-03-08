package ru.sablebot.api.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.sablebot.api.dto.stats.*
import ru.sablebot.api.security.annotation.RequireGuildPermission
import ru.sablebot.api.service.StatsService

@RestController
@RequestMapping("/api/guilds/{guildId}/stats")
class StatsController(
    private val statsService: StatsService
) {

    @GetMapping("/overview")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getOverview(@PathVariable guildId: String): ResponseEntity<StatsOverviewResponse> {
        return ResponseEntity.ok(statsService.getOverview(guildId.toLong()))
    }

    @GetMapping("/commands")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getCommandUsage(
        @PathVariable guildId: String,
        @RequestParam(defaultValue = "7d") period: String
    ): ResponseEntity<CommandUsageResponse> {
        return ResponseEntity.ok(statsService.getCommandUsage(guildId.toLong(), period))
    }

    @GetMapping("/members")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getMemberGrowth(
        @PathVariable guildId: String,
        @RequestParam(defaultValue = "30d") period: String
    ): ResponseEntity<MemberGrowthResponse> {
        return ResponseEntity.ok(statsService.getMemberGrowth(guildId.toLong(), period))
    }

    @GetMapping("/audio")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getAudioStats(
        @PathVariable guildId: String,
        @RequestParam(defaultValue = "7d") period: String
    ): ResponseEntity<AudioStatsResponse> {
        return ResponseEntity.ok(statsService.getAudioStats(guildId.toLong(), period))
    }
}
