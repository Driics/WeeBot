package ru.sablebot.worker.commands

import dev.minn.jda.ktx.messages.InlineMessage
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import ru.sablebot.common.worker.command.model.AbstractCommand
import ru.sablebot.common.worker.command.model.BotContext
import ru.sablebot.common.worker.command.model.DiscordCommand
import ru.sablebot.common.worker.message.model.InteractionMessage
import ru.sablebot.common.worker.message.model.styled
import kotlin.time.measureTime

@DiscordCommand(
    key = "ping",
    description = "Display ping",
)
class PingCommand : AbstractCommand() {
    override fun execute(event: SlashCommandInteractionEvent, context: BotContext) {
        var message: InteractionMessage

        fun buildPingMessage(apiLatency: Long?): InlineMessage<*>.() -> (Unit) = {
            styled(contentText = "**Pong!** (Shard ${discordService.jda.shardInfo.shardId} / ${workerProperties.discord.shardsTotal - 1}", prefix = ":ping_pong:")
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
            message = context.reply(false) {
                apply(buildPingMessage(null))
            }
        }

        message.editMessage {
            apply(buildPingMessage(apiPing.inWholeMilliseconds))
        }
    }
}