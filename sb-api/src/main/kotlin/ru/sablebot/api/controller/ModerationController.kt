package ru.sablebot.api.controller

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.sablebot.api.dto.moderation.*
import ru.sablebot.api.security.annotation.RequireGuildPermission
import ru.sablebot.api.security.utils.SecurityUtils
import ru.sablebot.api.service.ModerationApiService
import ru.sablebot.common.model.ModerationCaseType

@RestController
@RequestMapping("/api/guilds/{guildId}")
class ModerationController(
    private val moderationApiService: ModerationApiService
) {

    @GetMapping("/cases")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getCases(
        @PathVariable guildId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) type: ModerationCaseType?
    ): ResponseEntity<CaseListResponse> {
        return ResponseEntity.ok(moderationApiService.getCases(guildId.toLong(), page, size, type))
    }

    @GetMapping("/cases/{caseNumber}")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun getCase(
        @PathVariable guildId: String,
        @PathVariable caseNumber: Int
    ): ResponseEntity<CaseResponse> {
        val case = moderationApiService.getCase(guildId.toLong(), caseNumber)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(case)
    }

    @PostMapping("/moderation/ban")
    @RequireGuildPermission(RequireGuildPermission.BAN_MEMBERS)
    fun ban(
        @PathVariable guildId: String,
        @Valid @RequestBody request: BanRequest
    ): ResponseEntity<ModerationActionResponse> {
        val user = SecurityUtils.currentUser
            ?: return ResponseEntity.status(401).build()
        val userId = user.id
            ?: return ResponseEntity.status(401).build()
        moderationApiService.requestBan(guildId.toLong(), request, userId, user.userName ?: "Dashboard User")
        return ResponseEntity.ok(ModerationActionResponse(true, null, "Ban request sent"))
    }

    @PostMapping("/moderation/kick")
    @RequireGuildPermission(RequireGuildPermission.KICK_MEMBERS)
    fun kick(
        @PathVariable guildId: String,
        @Valid @RequestBody request: KickRequest
    ): ResponseEntity<ModerationActionResponse> {
        val user = SecurityUtils.currentUser
            ?: return ResponseEntity.status(401).build()
        val userId = user.id
            ?: return ResponseEntity.status(401).build()
        moderationApiService.requestKick(guildId.toLong(), request, userId, user.userName ?: "Dashboard User")
        return ResponseEntity.ok(ModerationActionResponse(true, null, "Kick request sent"))
    }

    @PostMapping("/moderation/warn")
    @RequireGuildPermission(RequireGuildPermission.MANAGE_SERVER)
    fun warn(
        @PathVariable guildId: String,
        @Valid @RequestBody request: WarnRequest
    ): ResponseEntity<ModerationActionResponse> {
        val user = SecurityUtils.currentUser
            ?: return ResponseEntity.status(401).build()
        val userId = user.id
            ?: return ResponseEntity.status(401).build()
        moderationApiService.requestWarn(guildId.toLong(), request, userId, user.userName ?: "Dashboard User")
        return ResponseEntity.ok(ModerationActionResponse(true, null, "Warn request sent"))
    }
}
