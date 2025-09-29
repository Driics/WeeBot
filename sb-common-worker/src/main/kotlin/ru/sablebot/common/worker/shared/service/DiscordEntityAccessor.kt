package ru.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.entities.Guild
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.GuildConfig
import ru.sablebot.common.service.ConfigService
import java.util.*


/*
TODO: add users and members
 */
@Service
open class DiscordEntityAccessor(
    protected val configService: ConfigService,
) {
    @Transactional
    open fun getOrCreate(guild: Guild): GuildConfig {
        val config = configService.getOrCreate(guild.idLong)
        return updateIfRequired(guild, config)
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
}