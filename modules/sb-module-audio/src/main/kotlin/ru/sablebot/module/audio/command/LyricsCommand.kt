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
import java.util.*

@Component
class LyricsCommand : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "lyrics", "Show lyrics for the current or specified track",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567819")
    ) {
        executor = LyricsExecutor()
    }

    inner class LyricsExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val query = optionalString("query", "Song name to search lyrics for")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                context.reply(ephemeral = true, "Lyrics feature is coming soon! Stay tuned.")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
