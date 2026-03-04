package ru.sablebot.api.controller

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.sablebot.api.dto.config.GuildConfigResponse
import ru.sablebot.api.dto.config.UpdateGuildConfigRequest
import ru.sablebot.api.security.annotation.RequireGuildPermission
import ru.sablebot.api.service.GuildConfigApiService

@RestController
@RequestMapping("/api/guilds/{guildId}")
class GuildConfigController(
    private val guildConfigApiService: GuildConfigApiService
) {

    @GetMapping("/config")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getConfig(@PathVariable guildId: String): ResponseEntity<GuildConfigResponse> {
        return ResponseEntity.ok(guildConfigApiService.getGuildConfig(guildId.toLong()))
    }

    @PatchMapping("/config")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun updateConfig(
        @PathVariable guildId: String,
        @Valid @RequestBody request: UpdateGuildConfigRequest
    ): ResponseEntity<GuildConfigResponse> {
        guildConfigApiService.updateGuildConfig(guildId.toLong(), request)
        return ResponseEntity.ok(guildConfigApiService.getGuildConfig(guildId.toLong()))
    }
}
