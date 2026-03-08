package ru.sablebot.module.audio.command

import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.audio.service.ILavalinkV4AudioService
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class JoinCommand(
    private val playerService: PlayerServiceV4,
    private val audioService: ILavalinkV4AudioService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "join", "Join a voice channel",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567816")
    ) {
        executor = JoinExecutor()
    }

    inner class JoinExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val channel = optionalChannel("channel", "Voice channel to join")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                val targetChannel = args[options.channel]

                val voiceChannel: VoiceChannel = if (targetChannel != null) {
                    targetChannel as? VoiceChannel
                        ?: throw DiscordException("Please select a voice channel.")
                } else {
                    AudioCommandPreconditions.requireVoiceChannel(member)
                }

                audioService.connect(voiceChannel)
                context.reply(ephemeral = false, "Joined **${voiceChannel.name}**.")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
