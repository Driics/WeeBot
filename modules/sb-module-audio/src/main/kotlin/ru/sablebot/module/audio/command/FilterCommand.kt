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
import ru.sablebot.common.worker.message.model.commands.options.StringDiscordOptionReference
import ru.sablebot.module.audio.model.FilterPreset
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class FilterCommand(
    private val playerService: PlayerServiceV4
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "filter", "Apply an audio filter preset",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567818")
    ) {
        executor = FilterExecutor()
    }

    inner class FilterExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val preset = string("preset", "Filter preset to apply") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Bass Boost", "BASSBOOST")
                choices += StringDiscordOptionReference.Choice.RawChoice("Nightcore", "NIGHTCORE")
                choices += StringDiscordOptionReference.Choice.RawChoice("Vaporwave", "VAPORWAVE")
                choices += StringDiscordOptionReference.Choice.RawChoice("Karaoke", "KARAOKE")
                choices += StringDiscordOptionReference.Choice.RawChoice("8D Audio", "EIGHT_D")
                choices += StringDiscordOptionReference.Choice.RawChoice("Tremolo", "TREMOLO")
                choices += StringDiscordOptionReference.Choice.RawChoice("Vibrato", "VIBRATO")
                choices += StringDiscordOptionReference.Choice.RawChoice("None (Remove)", "NONE")
            }
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                AudioCommandPreconditions.requireSameChannel(member, guild, playerService)
                AudioCommandPreconditions.requireActivePlayer(guild, playerService)

                context.deferChannelMessage(false)

                val presetStr = args[options.preset]
                val preset = FilterPreset.fromName(presetStr)
                    ?: throw DiscordException("Unknown filter preset.")

                playerService.applyFilter(guild, preset)
                if (preset == FilterPreset.NONE) {
                    context.reply(ephemeral = false, "Removed all audio filters.")
                } else {
                    context.reply(ephemeral = false, "Applied **${preset.displayName}** filter.")
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
