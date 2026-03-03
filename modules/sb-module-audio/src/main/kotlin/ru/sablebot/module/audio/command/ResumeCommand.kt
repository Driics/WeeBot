package ru.sablebot.module.audio.command

import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class ResumeCommand(
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "resume", "Resume the paused playback",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567803")
    ) {
        executor = ResumeExecutor()
    }

    inner class ResumeExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                AudioCommandPreconditions.requireSameChannel(member, guild, playerService)
                AudioCommandPreconditions.requireActivePlayer(guild, playerService)
                val resumed = playerService.resume(guild)
                if (resumed) {
                    context.reply(ephemeral = false, "Resumed playback.")
                } else {
                    context.reply(ephemeral = true, "Playback is not paused.")
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
