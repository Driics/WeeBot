package ru.sablebot.common.worker.command.service

import com.github.benmanes.caffeine.cache.Caffeine
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
import ru.sablebot.common.worker.command.model.Command
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.SlashCommandArgumentsSource
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.context.BotContext
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

    override fun isValidKey(event: SlashCommandInteractionEvent, key: String): Boolean = 
        holderService.isAnyCommand(key) || holderService.getDslCommandByName(key) != null

    override fun sendCommand(event: SlashCommandInteractionEvent): Boolean {
        val guild = event.guild ?: return false
        val channel = event.channel.asTextChannel()

        log.info { "Received event: $event" }

        // Try to find DSL command first
        val dslCommand = findDslCommand(event)
        if (dslCommand != null) {
            return executeDslCommand(event, dslCommand)
        }

        // Fallback to legacy command
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
    
    private fun findDslCommand(event: SlashCommandInteractionEvent): Pair<ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration, String>? {
        val commandName = event.name
        val subcommandGroupName = event.subcommandGroup
        val subcommandName = event.subcommandName
        
        return when {
            // Command with subcommand group
            subcommandGroupName != null && subcommandName != null -> {
                val fullName = "$commandName.$subcommandGroupName.$subcommandName"
                holderService.getDslCommandByName(fullName)?.let { it to fullName }
            }
            // Command with subcommand
            subcommandName != null -> {
                val fullName = "$commandName.$subcommandName"
                holderService.getDslCommandByName(fullName)?.let { it to fullName }
            }
            // Top-level command
            else -> {
                holderService.getDslCommandByName(commandName)?.let { it to commandName }
            }
        }
    }
    
    private fun executeDslCommand(
        event: SlashCommandInteractionEvent,
        commandPair: Pair<ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration, String>
    ): Boolean {
        val (declaration, fullName) = commandPair
        val executor = declaration.executor ?: run {
            log.warn { "DSL command $fullName has no executor" }
            return false
        }
        
        val guild = event.guild ?: return false
        val channel = event.channel.asTextChannel()
        
        // Check bot permissions
        if (declaration.botPermissions.isNotEmpty()) {
            val self = guild.selfMember
            if (!self.hasPermission(channel, *declaration.botPermissions.toTypedArray())) {
                val missingPermissions = declaration.botPermissions.filterNot { self.hasPermission(channel, it) }
                    .joinToString("\\n") { it.name }
                
                if (self.hasPermission(channel, Permission.MESSAGE_SEND)) {
                    messageService.sendMessageSilent(channel::sendMessage, "Missing permissions: $missingPermissions")
                }
                return true
            }
        }
        
        measureTime {
            try {
                if (workerProperties.commands.invokeLogging) {
                    log.info { "Invoke DSL command [$fullName]: ${event.options}" }
                }
                
                kotlinx.coroutines.runBlocking {
                    executor.execute(
                        ApplicationCommandContext(event),
                        SlashCommandArguments(SlashCommandArgumentsSource.SlashCommandArgumentsEventSource(event))
                    )
                }
            } catch (e: Exception) {
                log.error(e) { "DSL command [$fullName] execution error" }
            }
        }.also {
            if (it.inWholeMilliseconds > workerProperties.commands.executionThresholdMs) {
                log.warn {
                    "DSL command [$fullName] took too long ($it) to execute with args: ${event.options}"
                }
            }
        }
        
        return true
    }
}