package ru.sablebot.module.audio.command

import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class DisconnectCommand(
    private val playerService: PlayerServiceV4,
    private val audioService: ILavalinkV4AudioService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "disconnect", "Disconnect from the voice channel",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567817")
    ) {
        executor = DisconnectExecutor()
    }

    inner class DisconnectExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                AudioCommandPreconditions.requireSameChannel(member, guild, playerService)

                if (!audioService.isConnected(guild)) {
                    throw DiscordException("I'm not connected to any voice channel.")
                }

                playerService.stop(member, guild)
                audioService.disconnect(guild)
                context.reply(ephemeral = false, "Disconnected from the voice channel.")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
