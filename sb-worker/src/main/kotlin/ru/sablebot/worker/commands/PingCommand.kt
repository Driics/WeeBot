package ru.sablebot.worker.commands

import dev.minn.jda.ktx.messages.InlineMessage
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import ru.sablebot.common.worker.command.model.AbstractCommand
import ru.sablebot.common.worker.command.model.DiscordCommand
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.message.model.InteractionMessage
import ru.sablebot.common.worker.message.model.styled
import kotlin.time.measureTime

@DiscordCommand(
    key = "ping",
    description = "Display ping",
)
class PingCommand : AbstractCommand() {
    override fun execute(
        event: SlashCommandInteractionEvent,
        context: ApplicationCommandContext,
        args: SlashCommandArguments
    ) {
        var message: InteractionMessage

        fun buildPingMessage(apiLatency: Long?): InlineMessage<*>.() -> (Unit) = {
            styled(
                contentText = "**Pong!** (Shard ${discordService.jda.shardInfo.shardId + 1} / ${workerProperties.discord.shardsTotal})",
                prefix = ":ping_pong:"
            )
            styled(contentText = "JDA Ping: `${discordService.jda.gatewayPing}ms`", prefix = ":zap:")

            if (apiLatency != null)
                styled(
                    contentText = "API Ping: `${apiLatency}ms`",
                    prefix = ":zap:"
                )
            else
                styled(
                    contentText = "API Ping:`...ms`",
                    prefix = ":zap:"
                )
        }


        val apiPing = measureTime {
            message = context.reply(true) {
                apply(buildPingMessage(null))
            }
        }

        message.editMessage {
            apply(buildPingMessage(apiPing.inWholeMilliseconds))
        }
    }
}