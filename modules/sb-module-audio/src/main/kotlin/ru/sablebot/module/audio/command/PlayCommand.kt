package ru.sablebot.module.audio.command

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.command.util.CommandUuidGenerator
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.audio.service.PlayerServiceV4

@Component
class PlayCommand(
    private val playerService: PlayerServiceV4,
    private val uuidGenerator: CommandUuidGenerator
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "play", "Play a track or search for music",
        CommandCategory.MUSIC, uuidGenerator.generate(CommandCategory.MUSIC, "play")
    ) {
        executor = PlayExecutor()
    }

    inner class PlayExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val query = string("query", "Track URL or search query")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val member = context.member
            try {
                AudioCommandPreconditions.requireVoiceChannel(member)
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
                return
            }

            val hook = context.deferChannelMessage(false)
            try {
                val query = args[options.query]
                val channel = context.channel as TextChannel
                playerService.loadAndPlay(channel, member, query)
            } catch (e: DiscordException) {
                hook.editOriginal { content = e.message ?: "An error occurred" }
                return
            }
            hook.jdaHook.deleteOriginal().queue(null) {}
        }
    }
}
