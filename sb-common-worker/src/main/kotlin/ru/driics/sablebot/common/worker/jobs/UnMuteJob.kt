package ru.driics.sablebot.common.worker.jobs

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.quartz.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.driics.sablebot.common.worker.modules.moderation.service.MuteService
import ru.driics.sablebot.common.worker.shared.service.DiscordService
import ru.driics.sablebot.common.worker.shared.support.AbstractJob
import ru.driics.sablebot.common.worker.shared.support.rescheduleIn
import kotlin.time.Duration.Companion.minutes

@Component
class UnMuteJob : AbstractJob() {

    @Autowired
    private lateinit var discordService: DiscordService

    @Autowired
    private lateinit var muteService: MuteService

    companion object {
        const val ATTR_USER_ID = "userId"
        const val ATTR_GUILD_ID = "guildId"
        const val ATTR_GLOBAL_ID = "global"
        const val ATTR_CHANNEL_ID = "channelId"
        const val GROUP = "UnMuteJob-group"

        fun createDetails(global: Boolean, channel: TextChannel?, member: Member): JobDetail =
            JobBuilder.newJob(UnMuteJob::class.java)
                .withIdentity(if (channel != null) getKey(member, channel) else getKey(member))
                .usingJobData(ATTR_GUILD_ID, member.guild.id)
                .usingJobData(ATTR_GLOBAL_ID, global.toString())
                .usingJobData(ATTR_USER_ID, member.user.id)
                .apply { channel?.id?.let { usingJobData(ATTR_CHANNEL_ID, it) } }
                .build()

        fun getKey(member: Member): JobKey {
            val identity = buildString {
                append(GROUP)
                append("-")
                append(member.guild.id)
                append("-")
                append(member.user.id)
            }
            return JobKey(identity, GROUP)
        }

        fun getKey(member: Member, channel: TextChannel): JobKey {
            val identity = buildString {
                append(GROUP)
                append("-")
                append(member.guild.id)
                append("-")
                append(member.user.id)
                append("-")
                append(channel.id)
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
        val jobData = JobData.from(data) ?: return

        if (jobData.isComplete) {
            muteService.clearState(jobData.guildId, jobData.userId, jobData.channelId)
        }

        processGuildUnmute(jobExecutionContext, jobData)
    }

    private fun processGuildUnmute(
        context: JobExecutionContext,
        jobData: JobData
    ) {
        val guild = discordService.getGuildById(jobData.guildId) ?: return

        if (!guild.isLoaded) {
            context.rescheduleIn(1.minutes, this)
            return
        }

        val member = guild.getMemberById(jobData.userId) ?: return
        val channel = jobData.channelId?.let { guild.getTextChannelById(it) }

        muteService.unmute(null, channel, member)
    }

    private data class JobData(
        val userId: String,
        val guildId: Long,
        val channelId: String?,
        val global: Boolean
    ) {
        val isComplete: Boolean
            get() = channelId != null

        companion object {
            fun from(dataMap: JobDataMap): JobData? {
                val userId = dataMap.getString(ATTR_USER_ID)?.takeIf { it.isNotBlank() }
                val guildIdStr = dataMap.getString(ATTR_GUILD_ID)?.takeIf { it.isNotBlank() }
                val channelId = dataMap.getString(ATTR_CHANNEL_ID)?.takeIf { it.isNotBlank() }
                val global = dataMap.getString(ATTR_GLOBAL_ID)?.toBooleanStrictOrNull() ?: false

                return if (userId != null && guildIdStr != null) {
                    val guildId = guildIdStr.toLongOrNull() ?: return null
                    JobData(userId, guildId, channelId, global)
                } else null
            }
        }
    }
}

fun UnMuteJob.Companion.scheduleUnmute(
    member: Member,
    channel: TextChannel? = null,
    global: Boolean = false,
): JobDetail {
    return createDetails(global, channel, member)
}