package ru.sablebot.module.moderation.job

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.entities.UserSnowflake
import org.quartz.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.shared.service.DiscordService
import ru.sablebot.common.worker.shared.support.AbstractJob
import ru.sablebot.common.worker.shared.support.rescheduleIn
import kotlin.time.Duration.Companion.minutes

@Component
class UnBanJob : AbstractJob() {

    @Autowired
    private lateinit var discordService: DiscordService

    companion object {
        private val log = KotlinLogging.logger { }

        const val ATTR_USER_ID = "userId"
        const val ATTR_GUILD_ID = "guildId"
        const val GROUP = "UnBanJob-group"

        fun createDetails(guildId: String, userId: String): JobDetail =
            JobBuilder.newJob(UnBanJob::class.java)
                .withIdentity(getKey(guildId, userId))
                .usingJobData(ATTR_GUILD_ID, guildId)
                .usingJobData(ATTR_USER_ID, userId)
                .build()

        fun getKey(guildId: String, userId: String): JobKey {
            val identity = buildString {
                append(GROUP)
                append("-")
                append(guildId)
                append("-")
                append(userId)
            }
            return JobKey(identity, GROUP)
        }
    }

    override fun execute(jobExecutionContext: JobExecutionContext) {
        if (!discordService.isConnected()) {
            jobExecutionContext.rescheduleIn(1.minutes, this)
            return
        }

        val data = jobExecutionContext.jobDetail.jobDataMap
        val guildIdStr = data.getString(ATTR_GUILD_ID)?.takeIf { it.isNotBlank() }
        val userId = data.getString(ATTR_USER_ID)?.takeIf { it.isNotBlank() }

        if (guildIdStr == null || userId == null) {
            log.warn { "UnBanJob missing required job data: guildId=$guildIdStr, userId=$userId" }
            return
        }

        val guildId = guildIdStr.toLongOrNull()
        if (guildId == null) {
            log.warn { "UnBanJob invalid guildId: $guildIdStr" }
            return
        }

        val guild = discordService.getGuildById(guildId)
        if (guild == null) {
            log.warn { "UnBanJob guild not found: $guildId" }
            return
        }

        if (!guild.isLoaded) {
            jobExecutionContext.rescheduleIn(1.minutes, this)
            return
        }

        guild.unban(UserSnowflake.fromId(userId))
            .reason("Temporary ban expired")
            .queue(
                { log.info { "Unbanned user $userId from guild $guildId (temporary ban expired)" } },
                { error -> log.error(error) { "Failed to unban user $userId from guild $guildId" } }
            )
    }
}
