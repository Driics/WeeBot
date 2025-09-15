package ru.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.sablebot.common.utils.CommonUtils
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.message.service.MessageService
import java.awt.Color
import java.io.PrintWriter
import java.io.StringWriter

@Service
class EmergencyServiceImpl @Autowired constructor(
    private val workerProperties: WorkerProperties,
    private val discordService: DiscordService,
    private val messageService: MessageService
) : EmergencyService {
    private val emergencyChannelId = workerProperties.support.emergencyChannelId

    override fun error(message: String, throwable: Throwable?) {
        if (emergencyChannelId == 0L || !discordService.isConnected(workerProperties.support.guildId))
            return

        val channel = discordService.shardManager.getTextChannelById(emergencyChannelId) ?: return

        if (throwable == null) {
            messageService.sendMessageSilent(channel::sendMessage, "@here $message")
            return
        }

        val stackTrace = StringWriter().also {
            throwable.printStackTrace(PrintWriter(it))
        }.toString()

        val errorText = "`${throwable.message}`\n\n StackTrace: ```javascript\n${stackTrace}"
        val baseEmbed = messageService.getBaseEmbed().apply {
            setTitle(message)
            setColor(Color.RED)
            setAuthor(throwable.stackTrace[0].className)
            setDescription(CommonUtils.trimTo(errorText, 2045) + "```")
        }.build()

        val createData = MessageCreateBuilder().apply {
            setContent("@here")
            setEmbeds(baseEmbed)
        }
        messageService.sendMessageSilent(channel::sendMessage, createData.build())
    }

}