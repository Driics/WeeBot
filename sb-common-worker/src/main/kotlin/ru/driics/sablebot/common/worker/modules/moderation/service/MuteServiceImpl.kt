package ru.driics.sablebot.common.worker.modules.moderation.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.driics.sablebot.common.persistence.repository.MuteStateRepository

@Service
class MuteServiceImpl @Autowired constructor(
    private val muteStateRepository: MuteStateRepository,
    private val schedulerFactoryBean: SchedulerFactoryBean,
    // TODO
) : MuteService {
    companion object {
        const val MUTED_ROLE_NAME = "silence"
    }

    internal enum class PermissionMode {
        DENY, ALLOW, UNCHECKED
    }

    @Transactional
    override fun getMutedRole(guild: Guild): Role = getMutedRole(guild, true)

    private fun getMutedRole(guild: Guild, updeteable: Boolean): Role {
        val moderationConfig = if (updeteable) co
    }
}