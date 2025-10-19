package ru.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.GuildConfig
import ru.sablebot.common.persistence.entity.LocalUser
import ru.sablebot.common.persistence.repository.LocalUserRepository
import ru.sablebot.common.service.ConfigService
import ru.sablebot.common.service.UserService
import java.util.*


/*
TODO: add users and members
 */
@Service
open class DiscordEntityAccessor(
    protected val configService: ConfigService,
    protected val userService: UserService,
    protected val userRepository: LocalUserRepository
) {
    private val `$userLock`: Any = arrayOfNulls<Any>(0)

    @Transactional
    open fun getOrCreate(guild: Guild): GuildConfig {
        val config = configService.getOrCreate(guild.idLong)
        return updateIfRequired(guild, config)
    }

    @Transactional
    open fun getOrCreate(user: User): LocalUser? {
        if (!userService.isApplicable(user))
            return null

        var localUser = userService.get(user)
        if (localUser == null) {
            synchronized(`$userLock`) {
                localUser = userService.get(user)
                if (localUser == null) {
                    localUser = LocalUser().apply {
                        userId = user.id
                    }
                    updateIfRequired(user, localUser)
                    userRepository.flush()
                    return localUser
                }
            }
        }

        return updateIfRequired(user, localUser)
    }

    private fun updateIfRequired(guild: Guild, config: GuildConfig): GuildConfig {
        try {
            var shouldSave = false
            if (!Objects.equals(config.name, guild.name)) {
                config.name = guild.name
                shouldSave = true
            }
            if (!Objects.equals(config.iconUrl, guild.getIconUrl())) {
                config.iconUrl = guild.getIconUrl()
                shouldSave = true
            }
            if (shouldSave) {
                configService.save(config)
            }
        } catch (_: ObjectOptimisticLockingFailureException) {
            // it's ok to ignore optlock here, anyway it will be updated later
        }
        return config
    }

    private fun updateIfRequired(user: User?, localUser: LocalUser?): LocalUser? {
        if (localUser == null) return null

        return try {
            var shouldSave = localUser.id == null

            user?.let {
                if (it.name != localUser.name) {
                    localUser.name = it.name
                    shouldSave = true
                }
                if (it.discriminator != localUser.discriminator) {
                    localUser.discriminator = it.discriminator
                    shouldSave = true
                }
            }

            if (shouldSave) userService.save(localUser) else localUser
        } catch (_: ObjectOptimisticLockingFailureException) {
            localUser
        }
    }
}