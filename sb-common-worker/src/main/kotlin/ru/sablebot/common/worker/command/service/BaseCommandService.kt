package ru.sablebot.common.worker.command.service

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.message.service.MessageService

abstract class BaseCommandService @Autowired constructor(
    protected val workerProperties: WorkerProperties,
    protected val holderService: CommandsHolderService,
    protected val messageService: MessageService,
): CommandsService, CommandHandler {
    private val logger: Logger = LoggerFactory.getLogger(BaseCommandService::class.java)

    override fun handleSlashCommand(event: SlashCommandInteractionEvent): Boolean {
        if (!isValidKey(event, event.name)) {
            return false
        }

        logger.info("Received command: {}", event.name)

        return sendCommand(event)
    }
}