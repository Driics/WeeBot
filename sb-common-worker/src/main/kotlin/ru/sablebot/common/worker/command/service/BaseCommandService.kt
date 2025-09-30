package ru.sablebot.common.worker.command.service

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.message.service.MessageService
import ru.sablebot.common.worker.shared.service.DiscordEntityAccessor

abstract class BaseCommandService @Autowired constructor(
    protected val workerProperties: WorkerProperties,
    protected val holderService: CommandsHolderService,
    protected val messageService: MessageService,
    protected val entityAccessor: DiscordEntityAccessor
): CommandsService, CommandHandler {
    private val logger: Logger = LoggerFactory.getLogger(BaseCommandService::class.java)

    override fun handleSlashCommand(event: SlashCommandInteractionEvent): Boolean {
        // Use fullCommandName to support subcommands (e.g., "command subcommand" or "command group subcommand")
        val commandKey = event.fullCommandName
        
        if (!isValidKey(event, commandKey)) {
            return false
        }

        logger.info("Received command: {}", commandKey)

        return sendCommand(event)
    }
}