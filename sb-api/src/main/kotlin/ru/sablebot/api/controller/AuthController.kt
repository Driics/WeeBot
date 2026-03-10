package ru.sablebot.api.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.sablebot.api.dto.auth.GuildInfoResponse
import ru.sablebot.api.dto.auth.UserInfoResponse
import ru.sablebot.api.security.config.CookieProperties
import ru.sablebot.api.security.config.CorsProperties
import ru.sablebot.api.security.config.DiscordProperties
import ru.sablebot.api.security.filter.JwtAuthenticationFilter
import ru.sablebot.api.security.service.GuildPermissionService
import ru.sablebot.api.security.service.JwtTokenService
import ru.sablebot.api.security.utils.SecurityUtils
import ru.sablebot.api.service.DiscordApiService
import java.net.URLEncoder
import java.util.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val discordProperties: DiscordProperties,
    private val corsProperties: CorsProperties,
    private val cookieProperties: CookieProperties,
    private val discordApiService: DiscordApiService,
    private val jwtTokenService: JwtTokenService,
    private val guildPermissionService: GuildPermissionService
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        private const val OAUTH_STATE_COOKIE = "oauth_state"
    }

    @GetMapping("/discord")
    fun redirectToDiscord(response: HttpServletResponse) {
        val scopes = "identify guilds"
        val encodedRedirect = URLEncoder.encode(discordProperties.redirectUri, "UTF-8")
        val state = UUID.randomUUID().toString()

        val stateCookie = Cookie(OAUTH_STATE_COOKIE, state).apply {
            isHttpOnly = true
            secure = cookieProperties.secure
            path = "/api/auth"
            maxAge = 300 // 5 minutes
            setAttribute("SameSite", "Lax")
        }
        response.addCookie(stateCookie)

        val url = "https://discord.com/api/oauth2/authorize" +
                "?client_id=${discordProperties.clientId}" +
                "&redirect_uri=$encodedRedirect" +
                "&response_type=code" +
                "&scope=${URLEncoder.encode(scopes, "UTF-8")}" +
                "&state=${URLEncoder.encode(state, "UTF-8")}"
        response.sendRedirect(url)
    }

    @GetMapping("/discord/callback")
    fun handleCallback(
        @RequestParam code: String,
        @RequestParam state: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        // Verify OAuth2 state parameter
        val expectedState = request.cookies?.find { it.name == OAUTH_STATE_COOKIE }?.value
        if (expectedState == null || state == null || expectedState != state) {
            logger.warn { "OAuth2 state mismatch" }
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Invalid OAuth2 state")
            return
        }

        // Clear the state cookie
        val clearStateCookie = Cookie(OAUTH_STATE_COOKIE, "").apply {
            isHttpOnly = true
            secure = cookieProperties.secure
            path = "/api/auth"
            maxAge = 0
        }
        response.addCookie(clearStateCookie)

        val tokens = discordApiService.exchangeCode(code)
        val user = discordApiService.getCurrentUser(tokens.accessToken)

        val jwt = jwtTokenService.generateToken(user.id, user.username, user.avatar, tokens.accessToken)

        val cookie = Cookie(JwtAuthenticationFilter.COOKIE_NAME, jwt).apply {
            isHttpOnly = true
            secure = cookieProperties.secure
            path = "/"
            maxAge = 86400 // 24 hours
            setAttribute("SameSite", "Lax")
        }
        response.addCookie(cookie)

        val dashboardUrl = corsProperties.allowedOrigins.split(",").first().trim()
        response.sendRedirect("$dashboardUrl/dashboard")
    }

    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): ResponseEntity<Void> {
        val cookie = Cookie(JwtAuthenticationFilter.COOKIE_NAME, "").apply {
            isHttpOnly = true
            secure = cookieProperties.secure
            path = "/"
            maxAge = 0
            setAttribute("SameSite", "Lax")
        }
        response.addCookie(cookie)
        return ResponseEntity.ok().build()
    }

    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<UserInfoResponse> {
        val details = SecurityUtils.currentUser
            ?: return ResponseEntity.status(401).build()

        val userId = details.id ?: return ResponseEntity.status(401).build()
        val accessToken = details.accessToken

        val guilds = if (accessToken != null) {
            try {
                val botGuildIds = discordApiService.getBotGuildIds()
                val userGuilds = discordApiService.getUserGuilds(accessToken)

                // Cache permissions for later use in guild-specific endpoints
                guildPermissionService.cacheGuildsPermissions(
                    userId,
                    userGuilds.map { it.id to it.permissions }
                )

                userGuilds.map { guild ->
                    GuildInfoResponse(
                        id = guild.id,
                        name = guild.name,
                        icon = guild.icon,
                        permissions = guild.permissions,
                        botPresent = guild.id in botGuildIds
                    )
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to fetch guilds for user $userId" }
                emptyList()
            }
        } else {
            emptyList()
        }

        return ResponseEntity.ok(
            UserInfoResponse(
                id = userId,
                username = details.userName ?: "",
                avatar = details.avatar,
                guilds = guilds
            )
        )
    }
}
