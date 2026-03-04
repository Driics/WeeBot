package ru.sablebot.api.websocket

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import ru.sablebot.api.security.models.DiscordUserDetails
import ru.sablebot.api.security.service.JwtTokenService

@Component
class WebSocketAuthInterceptor(
    private val jwtTokenService: JwtTokenService
) : ChannelInterceptor {

    private val logger = KotlinLogging.logger {}

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null && StompCommand.CONNECT == accessor.command) {
            val token = accessor.getFirstNativeHeader("Authorization")
                ?.removePrefix("Bearer ")
                ?: accessor.getFirstNativeHeader("token")

            if (token != null) {
                try {
                    val claims = jwtTokenService.extractClaims(token)
                    val details = DiscordUserDetails.create(
                        mapOf(
                            "id" to claims.subject,
                            "username" to claims.get("username", String::class.java),
                            "avatar" to claims.get("avatar", String::class.java)
                        )
                    )

                    val auth = UsernamePasswordAuthenticationToken(
                        details, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
                    )
                    accessor.user = auth

                    logger.debug { "WebSocket authenticated for user ${claims.subject}" }
                } catch (_: Exception) {
                    logger.warn { "WebSocket connection rejected: invalid token" }
                    return null
                }
            } else {
                logger.warn { "WebSocket connection rejected: no token provided" }
                return null
            }
        }

        return message
    }
}
