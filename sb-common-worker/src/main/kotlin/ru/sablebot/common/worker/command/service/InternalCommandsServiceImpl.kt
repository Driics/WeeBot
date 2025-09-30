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
    discordEntityAccessor: DiscordEntityAccessor,
    private val coroutineLauncher: ru.sablebot.common.support.CoroutineLauncher
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

    /**
     * Обрабатывает входящее SlashCommandInteractionEvent, маршрутизируя его сначала в DSL‑команду по полному пути, а при отсутствии — в устаревшую команду по локализованному ключу.
     *
     * @param event Событие слеш‑команды, полученное от Discord.
     * @return `true`, если событие принято и обработано (включая случаи, когда дальнейшая обработка прекращена из‑за отсутствия разрешений или ошибок выполнения); `false`, если событие не применимо (нет гильдии) или для команды не найден соответствующий обработчик. */
    override fun sendCommand(event: SlashCommandInteractionEvent): Boolean {
        val guild = event.guild ?: return false
        val channel = event.channel.asTextChannel()

        log.info { "Received slash command event: ${event.name}, fullCommandName: ${event.fullCommandName}" }

        // Try to find DSL command first by full command name (supports subcommands)
        val fullCommandName = event.fullCommandName
        val dslCommand = holderService.getDslCommandByFullPath(fullCommandName)

        if (dslCommand != null) {
            log.info { "Found DSL command: ${dslCommand.name}" }
            return executeDslCommand(event, dslCommand, guild, channel)
        }

        log.debug { "No DSL command found for '$fullCommandName', trying legacy command with key '${event.name}'" }

        // Fall back to legacy command handling
        val command = holderService.getByLocale(localizedKey = event.name, anyLocale = true)
        if (command == null) {
            log.warn { "No command found (neither DSL nor legacy) for: ${event.fullCommandName}" }
            return false
        }

        log.info { "Found legacy command: ${command.key}" }
        return executeLegacyCommand(event, command, guild, channel)
    }

    /**
     * Обрабатывает и выполняет DSL-слэш-команду: проверяет права бота, сообщает о недостающих правах и запускает исполнение в корутине.
     *
     * @param event Событие взаимодействия слэш-команды.
     * @param dslCommand Описание DSL-команды, содержащая метаданные и исполнителя.
     * @param guild Сервер (Guild), в котором вызвана команда.
     * @param channel Текстовый канал для отправки ответов и сообщений об ошибках.
     * @return `true` если событие было обработано (включая случаи, когда выполнение было отменено из‑за недостатка прав), `false` если у команды отсутствует исполнитель и она не обработана.
     */
    private fun executeDslCommand(
        event: SlashCommandInteractionEvent,
        dslCommand: ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration,
        guild: net.dv8tion.jda.api.entities.Guild,
        channel: TextChannel
    ): Boolean {
        val executor = dslCommand.executor ?: run {
            log.warn { "DSL command ${dslCommand.name} has no executor" }
            return false
        }

        // Check bot permissions
        val requiredPermissions = dslCommand.botPermissions
        if (requiredPermissions.isNotEmpty()) {
            val self = guild.selfMember
            if (!self.hasPermission(channel, *requiredPermissions.toTypedArray())) {
                val missingPermissions = requiredPermissions.filterNot { self.hasPermission(channel, it) }
                    .joinToString("\n") { it.name }

                if (self.hasPermission(channel, Permission.MESSAGE_SEND)) {
                    if (self.hasPermission(channel, Permission.MESSAGE_EMBED_LINKS)) {
                        messageService.sendMessageSilent(channel::sendMessage, "Bot is missing required permissions")
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
                
                // Launch executor in coroutine scope since execute is a suspend function
                coroutineLauncher.launchMessageJob(event) {
                    executor.execute(
                        ApplicationCommandContext(event),
                        SlashCommandArguments(SlashCommandArgumentsSource.SlashCommandArgumentsEventSource(event))
                    )
                }
            } catch (e: DiscordException) {
                log.error(e) { "DSL Command [${dslCommand.name}] execution error" }
            } catch (e: Exception) {
                log.error(e) { "Unexpected error executing DSL command [${dslCommand.name}]" }
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

    /**
     * Обрабатывает legacy-реализацию slash-команды: проверяет отключение и права бота, выполняет команду и фиксирует слишком долгую обработку.
     *
     * @param event Событие взаимодействия slash-команды.
     * @param command Объект legacy-команды для выполнения.
     * @param guild Сервер (Guild), в контексте которого выполняется команда.
     * @param channel Текстовый канал, где была вызвана команда.
     * @return `true` если событие было обработано и не требует дальнейшей передачи. 
     */
    private fun executeLegacyCommand(
        event: SlashCommandInteractionEvent,
        command: Command,
        guild: net.dv8tion.jda.api.entities.Guild,
        channel: TextChannel
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
}