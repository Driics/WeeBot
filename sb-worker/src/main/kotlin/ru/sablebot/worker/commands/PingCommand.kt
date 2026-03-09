package ru.sablebot.worker.commands

import dev.minn.jda.ktx.messages.InlineMessage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.command.util.CommandUuidGenerator
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.message.model.InteractionMessage
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.common.worker.message.model.styled
import ru.sablebot.common.worker.shared.service.DiscordService
import kotlin.time.measureTime

@Component
class PingCommand(
    private val discordServiceProvider: ObjectProvider<DiscordService>,
    private val workerProperties: WorkerProperties,
    private val uuidGenerator: CommandUuidGenerator
) : SlashCommandDeclarationWrapper {
    private val discordService by lazy { discordServiceProvider.getObject() }

    override fun command() = slashCommand(
        "ping",
        "Display ping",
        CommandCategory.GENERAL,
        uuidGenerator.generate(CommandCategory.GENERAL, "ping")
    ) {
        executor = PingExecutor()
    }

    inner class PingExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions()

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
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
                        contentText = "API Ping: `...ms`",
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
}