package ru.sablebot.common.worker.command.service

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.worker.command.model.BotContext
import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.message.service.MessageService
import kotlin.time.measureTime

@Order(0)
@Service
class InternalCommandsServiceImpl @Autowired constructor(
    workerProperties: WorkerProperties,
    @Lazy holderService: CommandsHolderService,
    messageService: MessageService
) : BaseCommandService(workerProperties, holderService, messageService), InternalCommandsService {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override fun isApplicable(command: Command, user: User, member: Member, channel: TextChannel): Boolean =
        command.isAvailable(user, member, channel.guild)

    override fun isValidKey(event: SlashCommandInteractionEvent, key: String): Boolean = holderService.isAnyCommand(key)

    override fun sendCommand(event: SlashCommandInteractionEvent): Boolean {
        val guild = event.guild ?: return false
        val channel = event.channel.asTextChannel()

        log.info { "Received event: $event" }

        val command = holderService.getByLocale(localizedKey = event.name, anyLocale = true)
            ?: return false


        if (workerProperties.commands.disabled.contains(command.key))
            return true

        val requiredPermissions = command.permissions
        if (requiredPermissions.isNotEmpty()) {
            val self = guild.selfMember
            if (!self.hasPermission(channel, *requiredPermissions)) {
                val missingPermissions = requiredPermissions.filterNot { self.hasPermission(channel, it) }
                    .joinToString("\n") { it.name }

                if (self.hasPermission(channel, Permission.MESSAGE_SEND)) {
                    if (self.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
                        messageService.sendMessageSilent(channel::sendMessage, "Pizdec")
                    } else {
                        val title = "Error permissions"
                        messageService.sendMessageSilent(channel::sendMessage, "$title\n\n$missingPermissions")
                    }
                }

                return true
            }
        }

        measureTime {
            try {
                if (workerProperties.commands.invokeLogging) {
                    log.info { "Invoke command [${command::class.simpleName}]: ${event.options}" }
                }
                command.execute(event, BotContext(event))
            } catch (e: DiscordException) {
                log.error(e) { "Command [${command.key}] execution error" }
            }
        }.also {
            if (it.inWholeMilliseconds > workerProperties.commands.executionThresholdMs) {
                log.warn {
                    "Command [${command.javaClass.simpleName}] took too long ($it) to execute with args: ${event.options}"
                }
            }
        }

        return true
    }


}