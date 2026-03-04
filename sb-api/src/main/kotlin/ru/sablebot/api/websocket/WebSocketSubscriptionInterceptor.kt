package ru.sablebot.api.websocket

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import ru.sablebot.api.security.models.DiscordUserDetails
import ru.sablebot.api.security.service.GuildPermissionService

@Component
class WebSocketSubscriptionInterceptor(
    private val guildPermissionService: GuildPermissionService
) : ChannelInterceptor {

    private val logger = KotlinLogging.logger {}

    private val guildTopicPattern = Regex("/topic/guild\\.(\\d+)\\..+")

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        if (StompCommand.SUBSCRIBE != accessor.command) {
            return message
        }

        val destination = accessor.destination ?: return message
        val match = guildTopicPattern.matchEntire(destination) ?: return message
        val guildId = match.groupValues[1]

        val auth = accessor.user as? UsernamePasswordAuthenticationToken
        val details = auth?.principal as? DiscordUserDetails
        val userId = details?.id
            ?: throw AccessDeniedException("Not authenticated")

        if (!guildPermissionService.hasPermission(userId, guildId, MANAGE_SERVER_PERMISSION)) {
            logger.warn { "WebSocket subscription denied for user $userId to $destination" }
            throw AccessDeniedException("Insufficient permissions for guild $guildId")
        }

        return message
    }

    companion object {
        private const val MANAGE_SERVER_PERMISSION = 0x00000020L
    }
}
