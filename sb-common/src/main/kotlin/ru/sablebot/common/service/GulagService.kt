package ru.sablebot.common.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.Gulag
import ru.sablebot.common.persistence.repository.GulagRepository

@Service
class GulagService(
    private val gulagRepository: GulagRepository,
    private val userService: UserService
) {
    sealed class GulagResult {
        object Success : GulagResult()
        object AlreadyExists : GulagResult()
        object ModeratorNotFound : GulagResult()
    }

    @Transactional
    fun send(
        moderator: Member,
        snowflake: Long,
        reason: String,
    ): GulagResult {
        if (gulagRepository.existsBySnowflake(snowflake)) {
            return GulagResult.AlreadyExists
        }

        val user = userService.get(moderator.user)
            ?: return GulagResult.ModeratorNotFound


        return try {
            gulagRepository.save(
                Gulag(
                    snowflake = snowflake,
                    reason = reason,
                    moderator = user
                )
            )
            GulagResult.Success
        } catch (_: DataIntegrityViolationException) {
            GulagResult.AlreadyExists
        }
    }

    @Transactional
    fun send(
        moderator: Member,
        member: Member,
        reason: String,
    ): GulagResult = send(moderator, member.idLong, reason)

    /**
     * Retrieves Gulag record for a guild.
     * Checks guild owner first, then the guild itself.
     *
     * @param guild the guild to check
     * @return Gulag record if found, null otherwise
     */
    @Transactional(readOnly = true)
    fun getGulag(guild: Guild): Gulag? =
        gulagRepository.findBySnowflake(guild.ownerIdLong)
            ?: gulagRepository.findBySnowflake(guild.idLong)

    @Transactional(readOnly = true)
    fun getGulag(user: User): Gulag? = gulagRepository.findBySnowflake(user.idLong)
}