package ru.sablebot.module.audio.command

import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class MoveCommand(
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "move", "Move a track to a different position in the queue",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567814")
    ) {
        executor = MoveExecutor()
    }

    inner class MoveExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val from = string("from", "Current position of the track")
            val to = string("to", "New position for the track")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                AudioCommandPreconditions.requireSameChannel(member, guild, playerService)
                AudioCommandPreconditions.requireActivePlayer(guild, playerService)

                val from = args[options.from].toIntOrNull()
                    ?: throw DiscordException("Please provide a valid 'from' position.")
                val to = args[options.to].toIntOrNull()
                    ?: throw DiscordException("Please provide a valid 'to' position.")

                val moved = playerService.moveTo(guild, from - 1, to - 1)
                if (moved) {
                    context.reply(ephemeral = false, "Moved track from position **$from** to **$to**.")
                } else {
                    context.reply(ephemeral = true, "Could not move the track. Make sure both positions are valid.")
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
