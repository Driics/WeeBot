package ru.sablebot.worker.rabbit

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.Guild
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.shared.service.DiscordService

@Component
abstract class BaseQueueListener {
    @Autowired
    protected lateinit var discordService: DiscordService

    @Autowired
    protected lateinit var workerProperties: WorkerProperties

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    protected fun getGuildById(guildId: Long): Guild? {
        if (!discordService.isConnected(guildId)) {
            return null
        }

        return discordService.getGuildById(guildId)
    }
}