package ru.driics.sablebot.common.worker.command.model

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import ru.driics.sablebot.common.configuration.CommonProperties
import ru.driics.sablebot.common.worker.command.service.CommandsService
import ru.driics.sablebot.common.worker.command.service.InternalCommandsService
import ru.driics.sablebot.common.worker.configuration.WorkerProperties
import ru.driics.sablebot.common.worker.message.service.MessageService
import ru.driics.sablebot.common.worker.shared.service.DiscordService


abstract class AbstractCommand : Command {

    @Autowired
    @Lazy
    protected lateinit var discordService: DiscordService

    @Autowired
    protected lateinit var commonProperties: CommonProperties

    @Autowired
    protected lateinit var workerProperties: WorkerProperties

    @Autowired
    @Lazy
    protected lateinit var commandsService: CommandsService

    @Autowired
    @Lazy
    protected lateinit var messageService: MessageService

    override fun isAvailable(user: User, membere: Member, guild: Guild) = true

    override val annotation: DiscordCommand by lazy { javaClass.getAnnotation(DiscordCommand::class.java) }

}