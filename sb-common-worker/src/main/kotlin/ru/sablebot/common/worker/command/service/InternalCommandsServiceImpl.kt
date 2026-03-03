package ru.sablebot.common.worker.command.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.MDC
import io.micrometer.core.instrument.Timer
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
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
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.common.worker.shared.service.DiscordEntityAccessor

@Order(0)
@Service
class InternalCommandsServiceImpl @Autowired constructor(
    workerProperties: WorkerProperties,
    @Lazy holderService: CommandsHolderService,
    messageService: MessageService,
    discordEntityAccessor: DiscordEntityAccessor,
    private val coroutineLauncher: ru.sablebot.common.support.CoroutineLauncher,
    private val meterRegistry: MeterRegistry
) : BaseCommandService(workerProperties, holderService, messageService, discordEntityAccessor),
    InternalCommandsService {

    companion object {
        private val log = KotlinLogging.logger { }
    }

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
        val guildChannel = event.guildChannel

        log.info { "Received slash command event: ${event.name}, fullCommandName: ${event.fullCommandName}" }

        // Try to find DSL command first by full command name (supports subcommands)
        val fullCommandName = event.fullCommandName
        val dslCommand = holderService.getDslCommandByFullPath(fullCommandName)

        if (dslCommand != null) {
            log.info { "Found DSL command: ${dslCommand.name}" }
            return executeDslCommand(event, dslCommand, guild, guildChannel)
        }

        log.debug { "No DSL command found for '$fullCommandName', trying legacy command with key '${event.name}'" }

        // Fall back to legacy command handling
        val command = holderService.getByLocale(localizedKey = event.name, anyLocale = true)
        if (command == null) {
            log.warn { "No command found (neither DSL nor legacy) for: ${event.fullCommandName}" }
            return false
        }

        log.info { "Found legacy command: ${command.key}" }
        return executeLegacyCommand(event, command, guild, guildChannel)
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
        channel: GuildChannel
    ): Boolean {
        val executor = dslCommand.executor ?: run {
            log.warn { "DSL command ${dslCommand.name} has no executor" }
            return false
        }

        if (!checkBotPermissions(event, dslCommand.botPermissions, guild, channel)) return true

        if (workerProperties.commands.invokeLogging) {
            log.info { "Invoke DSL command [${dslCommand.name}]: ${event.options}" }
        }

        MDC.put("commandName", event.fullCommandName)
        MDC.put("guildId", event.guild?.id ?: "DM")
        MDC.put("userId", event.user.id)

        coroutineLauncher.launchMessageJob(event) {
            val sample = Timer.start(meterRegistry)
            try {
                executor.execute(
                    ApplicationCommandContext(
                        event,
                        entityAccessor.getOrCreate(guild),
                        entityAccessor.getOrCreate(event.user)
                    ),
                    SlashCommandArguments(SlashCommandArgumentsSource.SlashCommandArgumentsEventSource(event))
                )
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_EXECUTED_COUNTER,
                    "command", dslCommand.name, "type", "dsl", "outcome", "success"
                ).increment()
            } catch (e: DiscordException) {
                safeReplyError(event)
                log.error(e) { "DSL Command [${dslCommand.name}] execution error" }
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_ERRORS_COUNTER,
                    "command", dslCommand.name, "type", "dsl", "error_type", "discord"
                ).increment()
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_EXECUTED_COUNTER,
                    "command", dslCommand.name, "type", "dsl", "outcome", "error"
                ).increment()
            } catch (e: Exception) {
                safeReplyError(event)
                log.error(e) { "Unexpected error executing DSL command [${dslCommand.name}]" }
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_ERRORS_COUNTER,
                    "command", dslCommand.name, "type", "dsl", "error_type", "unexpected"
                ).increment()
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_EXECUTED_COUNTER,
                    "command", dslCommand.name, "type", "dsl", "outcome", "error"
                ).increment()
            } finally {
                sample.stop(
                    meterRegistry.timer(
                        InternalCommandsService.COMMANDS_DURATION_TIMER,
                        "command", dslCommand.name, "type", "dsl"
                    )
                )
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
        channel: GuildChannel
    ): Boolean {
        if (workerProperties.commands.disabled.contains(command.key))
            return true

        if (!checkBotPermissions(event, command.permissions.toList(), guild, channel)) return true

        // Check member permissions
        val memberPerms = command.annotation.memberRequiredPermissions
        if (memberPerms.isNotEmpty()) {
            val member = event.member ?: return true
            if (!member.hasPermission(event.guildChannel, *memberPerms)) {
                val missing = memberPerms
                    .filterNot { member.hasPermission(event.guildChannel, it) }
                    .joinToString(", ") { "`${it.name}`" }
                event.reply("Недостаточно прав: $missing").setEphemeral(true).queue()
                return true
            }
        }

        if (workerProperties.commands.invokeLogging) {
            log.info { "Invoke command [${command::class.simpleName}]: ${event.options}" }
        }

        MDC.put("commandName", command.key)
        MDC.put("guildId", event.guild?.id ?: "DM")
        MDC.put("userId", event.user.id)

        coroutineLauncher.launchMessageJob(event) {
            val sample = Timer.start(meterRegistry)
            try {
                command.execute(
                    event,
                    ApplicationCommandContext(
                        event,
                        entityAccessor.getOrCreate(guild),
                        entityAccessor.getOrCreate(event.user)
                    ),
                    SlashCommandArguments(SlashCommandArgumentsSource.SlashCommandArgumentsEventSource(event))
                )
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_EXECUTED_COUNTER,
                    "command", command.key, "type", "legacy", "outcome", "success"
                ).increment()
            } catch (e: DiscordException) {
                safeReplyError(event)
                log.error(e) { "Command [${command.key}] execution error" }
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_ERRORS_COUNTER,
                    "command", command.key, "type", "legacy", "error_type", "discord"
                ).increment()
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_EXECUTED_COUNTER,
                    "command", command.key, "type", "legacy", "outcome", "error"
                ).increment()
            } catch (e: Exception) {
                safeReplyError(event)
                log.error(e) { "Unexpected error executing command [${command.key}]" }
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_ERRORS_COUNTER,
                    "command", command.key, "type", "legacy", "error_type", "unexpected"
                ).increment()
                meterRegistry.counter(
                    InternalCommandsService.COMMANDS_EXECUTED_COUNTER,
                    "command", command.key, "type", "legacy", "outcome", "error"
                ).increment()
            } finally {
                sample.stop(
                    meterRegistry.timer(
                        InternalCommandsService.COMMANDS_DURATION_TIMER,
                        "command", command.key, "type", "legacy"
                    )
                )
            }
        }

        return true
    }

    private fun safeReplyError(event: SlashCommandInteractionEvent) {
        try {
            if (event.isAcknowledged) {
                event.hook.sendMessage("Произошла ошибка при выполнении команды").setEphemeral(true).queue()
            } else {
                event.reply("Произошла ошибка при выполнении команды").setEphemeral(true).queue()
            }
        } catch (_: Exception) { /* interaction expired or already handled */
        }
    }

    private fun checkBotPermissions(
        event: SlashCommandInteractionEvent,
        permissions: Collection<net.dv8tion.jda.api.Permission>,
        guild: net.dv8tion.jda.api.entities.Guild,
        channel: GuildChannel
    ): Boolean {
        if (permissions.isEmpty()) return true
        val self = guild.selfMember
        if (self.hasPermission(channel, *permissions.toTypedArray())) return true

        val missing = permissions.filterNot { self.hasPermission(channel, it) }
            .joinToString(", ") { "`${it.name}`" }
        event.reply("Недостаточно прав бота:\n$missing").setEphemeral(true).queue()
        return false
    }
}