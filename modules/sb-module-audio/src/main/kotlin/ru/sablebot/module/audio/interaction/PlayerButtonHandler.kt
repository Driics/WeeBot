package ru.sablebot.module.audio.interaction

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.sablebot.common.service.MusicConfigService
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.sablebot.module.audio.model.RepeatMode
import ru.sablebot.module.audio.service.PlayerServiceV4
import ru.sablebot.module.audio.service.helper.AudioMessageManager
import ru.sablebot.module.audio.service.helper.AudioMessageManager.Companion.BTN_PAUSE
import ru.sablebot.module.audio.service.helper.AudioMessageManager.Companion.BTN_REPEAT
import ru.sablebot.module.audio.service.helper.AudioMessageManager.Companion.BTN_RESUME
import ru.sablebot.module.audio.service.helper.AudioMessageManager.Companion.BTN_SHUFFLE
import ru.sablebot.module.audio.service.helper.AudioMessageManager.Companion.BTN_SKIP
import ru.sablebot.module.audio.service.helper.AudioMessageManager.Companion.BTN_STOP

@Component
class PlayerButtonHandler(
    @Lazy private val playerService: PlayerServiceV4,
    private val musicConfigService: MusicConfigService,
    @Lazy private val messageManager: AudioMessageManager
) : DiscordEventListener() {

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val AUDIO_PREFIX = "audio:"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val customId = event.componentId
        if (!customId.startsWith(AUDIO_PREFIX)) return
        // Queue pagination is handled by QueuePaginationHandler
        if (customId.startsWith("audio:queue:")) return

        val guild = event.guild ?: return
        val member = event.member ?: return

        // Check preconditions
        if (!checkPreconditions(event, member, guild)) return

        // Route to handler
        when (customId) {
            BTN_PAUSE -> handlePause(event, guild)
            BTN_RESUME -> handleResume(event, guild)
            BTN_SKIP -> handleSkip(event, member, guild)
            BTN_STOP -> handleStop(event, member, guild)
            BTN_REPEAT -> handleRepeat(event, guild)
            BTN_SHUFFLE -> handleShuffle(event, guild)
            else -> {
                // Unknown audio button, ignore
                event.deferEdit().queue()
            }
        }
    }

    private fun checkPreconditions(event: ButtonInteractionEvent, member: Member, guild: Guild): Boolean {
        // Check if player is active
        if (!playerService.isActive(guild)) {
            event.reply("Nothing is playing right now.").setEphemeral(true).queue()
            return false
        }

        // Check if user has access
        if (!playerService.hasAccess(member)) {
            event.reply("You don't have permission to control the player.").setEphemeral(true).queue()
            return false
        }

        // Check if user is in a voice channel
        if (!playerService.isInChannel(member)) {
            event.reply("You need to be in the same voice channel as the bot.").setEphemeral(true).queue()
            return false
        }

        return true
    }

    private fun handlePause(event: ButtonInteractionEvent, guild: Guild) {
        event.deferEdit().queue()
        scope.launch {
            try {
                playerService.pause(guild)
                messageManager.refreshPanel(guild.idLong)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to pause for guild ${guild.idLong}" }
            }
        }
    }

    private fun handleResume(event: ButtonInteractionEvent, guild: Guild) {
        event.deferEdit().queue()
        scope.launch {
            try {
                playerService.resume(guild)
                messageManager.refreshPanel(guild.idLong)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to resume for guild ${guild.idLong}" }
            }
        }
    }

    private fun handleSkip(event: ButtonInteractionEvent, member: Member, guild: Guild) {
        event.deferEdit().queue()
        try {
            playerService.skipTrack(member, guild)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to skip for guild ${guild.idLong}" }
        }
    }

    private fun handleStop(event: ButtonInteractionEvent, member: Member, guild: Guild) {
        event.deferEdit().queue()
        try {
            playerService.stop(member, guild)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to stop for guild ${guild.idLong}" }
        }
    }

    private fun handleRepeat(event: ButtonInteractionEvent, guild: Guild) {
        event.deferEdit().queue()
        val instance = playerService.get(guild) ?: return

        val nextMode = when (instance.mode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.CURRENT
            RepeatMode.CURRENT -> RepeatMode.NONE
        }

        instance.mode = nextMode
        messageManager.refreshPanel(guild.idLong)
    }

    private fun handleShuffle(event: ButtonInteractionEvent, guild: Guild) {
        event.deferEdit().queue()
        val shuffled = playerService.shuffle(guild)
        if (shuffled) {
            messageManager.refreshPanel(guild.idLong)
        }
    }
}
