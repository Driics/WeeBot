package ru.sablebot.module.moderation.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import ru.sablebot.common.support.CoroutineLauncher
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.module.moderation.service.IAutoModService

@DiscordEvent
class AutoModMessageListener(
    private val autoModService: IAutoModService,
    private val coroutineLauncher: CoroutineLauncher
) : DiscordEventListener() {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!event.isFromGuild) return
        if (event.author.isBot) return
        if (event.author.isSystem) return

        val member = event.member ?: return

        // Skip members with moderation permissions
        if (member.hasPermission(Permission.ADMINISTRATOR)) return
        if (member.hasPermission(Permission.MESSAGE_MANAGE)) return

        coroutineLauncher.launchMessageJob(event) {
            try {
                autoModService.onMessage(event.message)
            } catch (e: Exception) {
                logger.error(e) { "AutoMod message processing failed for message ${event.messageId}" }
            }
        }
    }
}
