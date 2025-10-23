package ru.sablebot.common.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.Gulag
import ru.sablebot.common.persistence.repository.GulagRepository

@Service
class GulagService(
    private val gulagRepository: GulagRepository,
    private val userService: UserService
) {
    @Transactional
    fun send(
        moderator: Member,
        snowflake: Long,
        reason: String,
    ): Boolean {
        if (gulagRepository.existsBySnowflake(snowflake)) {
            return false
        }

        userService.get(moderator.user)?.let { user ->
            gulagRepository.save(
                Gulag(
                    snowflake = snowflake,
                    reason = reason,
                    moderator = user
                )
            )
            return true
        }

        return false
    }

    @Transactional
    fun send(
        moderator: Member,
        member: Member,
        reason: String,
    ): Boolean = send(moderator, member.idLong, reason)

    @Transactional(readOnly = true)
    fun getGulag(guild: Guild): Gulag? =
        gulagRepository.findBySnowflake(guild.ownerIdLong)
            ?: gulagRepository.findBySnowflake(guild.idLong)

    @Transactional(readOnly = true)
    fun getGulag(user: User): Gulag? = gulagRepository.findBySnowflake(user.idLong)
}