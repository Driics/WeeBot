package ru.sablebot.common.worker.command.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
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
import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.SlashCommandArgumentsSource
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.context.BotContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.common.worker.shared.service.DiscordEntityAccessor
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime

@Order(0)
@Service
class InternalCommandsServiceImpl @Autowired constructor(
    workerProperties: WorkerProperties,
    @Lazy holderService: CommandsHolderService,
    messageService: MessageService,
    discordEntityAccessor: DiscordEntityAccessor
) : BaseCommandService(workerProperties, holderService, messageService, discordEntityAccessor),
    InternalCommandsService {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    val contexts = Caffeine
        .newBuilder()
        .expireAfterAccess(1, TimeUnit.DAYS)
        .build<Long, BotContext>()

    override fun isApplicable(command: Command, user: User, member: Member, channel: TextChannel): Boolean =
        command.isAvailable(user, member, channel.guild)

    override fun isValidKey(event: SlashCommandInteractionEvent, key: String): Boolean = holderService.isAnyCommand(key)

    override fun sendCommand(event: SlashCommandInteractionEvent): Boolean {
        val guild = event.guild ?: return false
        val channel = event.channel.asTextChannel()

        log.info { "Received event: $event" }

        // Try legacy command first
        val command = holderService.getByLocale(localizedKey = event.name, anyLocale = true)

        if (command != null) {
            return executeLegacyCommand(event, guild, channel, command)
        }

        // Try DSL command
        return executeDslCommand(event, guild, channel)
    }

    private fun executeLegacyCommand(
        event: SlashCommandInteractionEvent,
        guild: net.dv8tion.jda.api.entities.Guild,
        channel: TextChannel,
        command: Command
    ): Boolean {
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
                command.execute(
                    event,
                    ApplicationCommandContext(event),
                    SlashCommandArguments(SlashCommandArgumentsSource.SlashCommandArgumentsEventSource(event))
                )
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

    private fun executeDslCommand(
        event: SlashCommandInteractionEvent,
        guild: net.dv8tion.jda.api.entities.Guild,
        channel: TextChannel
    ): Boolean {
        val holderServiceImpl = holderService as? CommandsHolderServiceImpl ?: return false
        val dslCommand = findDslCommand(holderServiceImpl.dslCommands, event.fullCommandName) ?: return false

        val executor = dslCommand.executor ?: run {
            log.warn { "DSL command '${event.fullCommandName}' has no executor" }
            return false
        }

        if (workerProperties.commands.disabled.contains(dslCommand.name))
            return true

        val requiredPermissions = dslCommand.botPermissions
        if (requiredPermissions.isNotEmpty()) {
            val self = guild.selfMember
            if (!self.hasPermission(channel, *requiredPermissions.toTypedArray())) {
                val missingPermissions = requiredPermissions.filterNot { self.hasPermission(channel, it) }
                    .joinToString("\n") { it.name }

                if (self.hasPermission(channel, Permission.MESSAGE_SEND)) {
                    if (self.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
                        messageService.sendMessageSilent(channel::sendMessage, "Missing permissions")
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
                    log.info { "Invoke DSL command [${dslCommand.name}]: ${event.options}" }
                }
                runBlocking {
                    executor.execute(
                        ApplicationCommandContext(event),
                        SlashCommandArguments(SlashCommandArgumentsSource.SlashCommandArgumentsEventSource(event))
                    )
                }
            } catch (e: Exception) {
                log.error(e) { "DSL Command [${dslCommand.name}] execution error" }
            }
        }.also {
            if (it.inWholeMilliseconds > workerProperties.commands.executionThresholdMs) {
                log.warn {
                    "DSL Command [${dslCommand.name}] took too long ($it) to execute with args: ${event.options}"
                }
            }
        }

        return true
    }

    private fun findDslCommand(
        dslCommands: List<ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper>,
        fullCommandName: String
    ): SlashCommandDeclaration? {
        val parts = fullCommandName.split(" ")
        
        for (wrapper in dslCommands) {
            val command = wrapper.command().build()
            
            if (command.name == parts[0]) {
                if (parts.size == 1) {
                    return command
                }
                
                // Handle subcommand groups
                if (parts.size == 3) {
                    val group = command.subcommandGroups.find { it.name == parts[1] }
                    if (group != null) {
                        return group.subcommands.find { it.name == parts[2] }
                    }
                }
                
                // Handle subcommands
                if (parts.size == 2) {
                    return command.subcommands.find { it.name == parts[1] }
                }
            }
        }
        
        return null
    }
}