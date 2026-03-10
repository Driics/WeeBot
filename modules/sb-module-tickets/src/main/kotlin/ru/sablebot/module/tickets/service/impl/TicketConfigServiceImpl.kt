package ru.sablebot.module.tickets.service.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import net.dv8tion.jda.api.entities.Guild
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.common.persistence.entity.TicketCategory
import ru.sablebot.common.persistence.entity.TicketConfig
import ru.sablebot.common.persistence.repository.TicketCategoryRepository
import ru.sablebot.common.persistence.repository.TicketConfigRepository
import ru.sablebot.module.tickets.service.ITicketConfigService

@Service
open class TicketConfigServiceImpl(
    private val configRepository: TicketConfigRepository,
    private val categoryRepository: TicketCategoryRepository,
    private val meterRegistry: MeterRegistry
) : ITicketConfigService {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    private fun recordAction(type: String) {
        meterRegistry.counter("sablebot.tickets.config", "type", type).increment()
    }

    override fun getConfig(guildId: Long): TicketConfig? {
        return configRepository.findByGuildId(guildId)
    }

    override fun getConfig(guild: Guild): TicketConfig? {
        return getConfig(guild.idLong)
    }

    @Transactional
    override fun getOrCreateConfig(guildId: Long): TicketConfig {
        return configRepository.findByGuildId(guildId) ?: run {
            log.info { "Creating new ticket config for guild $guildId" }
            recordAction("create")
            val config = TicketConfig(guildId)
            configRepository.save(config)
        }
    }

    override fun getOrCreateConfig(guild: Guild): TicketConfig {
        return getOrCreateConfig(guild.idLong)
    }

    @Transactional
    override fun saveConfig(config: TicketConfig): TicketConfig {
        recordAction("save")
        return configRepository.save(config)
    }

    override fun isEnabled(guildId: Long): Boolean {
        return getConfig(guildId)?.enabled ?: false
    }

    @Transactional
    override fun enableTickets(guildId: Long): TicketConfig {
        recordAction("enable")
        val config = getOrCreateConfig(guildId)
        config.enabled = true
        log.info { "Enabled tickets for guild $guildId" }
        return configRepository.save(config)
    }

    @Transactional
    override fun disableTickets(guildId: Long): TicketConfig {
        recordAction("disable")
        val config = getOrCreateConfig(guildId)
        config.enabled = false
        log.info { "Disabled tickets for guild $guildId" }
        return configRepository.save(config)
    }

    @Transactional
    override fun setSupportChannel(guildId: Long, channelId: String?): TicketConfig {
        recordAction("set_support_channel")
        val config = getOrCreateConfig(guildId)
        config.supportChannelId = channelId
        log.debug { "Set support channel for guild $guildId to $channelId" }
        return configRepository.save(config)
    }

    @Transactional
    override fun setCategoryChannel(guildId: Long, channelId: String?): TicketConfig {
        recordAction("set_category_channel")
        val config = getOrCreateConfig(guildId)
        config.categoryChannelId = channelId
        log.debug { "Set category channel for guild $guildId to $channelId" }
        return configRepository.save(config)
    }

    @Transactional
    override fun setTranscriptChannel(guildId: Long, channelId: String?): TicketConfig {
        recordAction("set_transcript_channel")
        val config = getOrCreateConfig(guildId)
        config.transcriptChannelId = channelId
        log.debug { "Set transcript channel for guild $guildId to $channelId" }
        return configRepository.save(config)
    }

    @Transactional
    override fun setMaxTicketsPerUser(guildId: Long, max: Int): TicketConfig {
        recordAction("set_max_tickets")
        require(max > 0) { "Max tickets per user must be greater than 0" }
        val config = getOrCreateConfig(guildId)
        config.maxTicketsPerUser = max
        log.debug { "Set max tickets per user for guild $guildId to $max" }
        return configRepository.save(config)
    }

    @Transactional
    override fun setAutoCloseInactiveDays(guildId: Long, days: Int?): TicketConfig {
        recordAction("set_auto_close")
        if (days != null) {
            require(days > 0) { "Auto-close inactive days must be greater than 0" }
        }
        val config = getOrCreateConfig(guildId)
        config.autoCloseInactiveDays = days
        log.debug { "Set auto-close inactive days for guild $guildId to $days" }
        return configRepository.save(config)
    }

    @Transactional
    override fun setDmOnClose(guildId: Long, enabled: Boolean): TicketConfig {
        recordAction("set_dm_on_close")
        val config = getOrCreateConfig(guildId)
        config.dmOnClose = enabled
        log.debug { "Set DM on close for guild $guildId to $enabled" }
        return configRepository.save(config)
    }

    @Transactional
    override fun addStaffRole(guildId: Long, roleId: String): TicketConfig {
        recordAction("add_staff_role")
        val config = getOrCreateConfig(guildId)
        if (!config.staffRoleIds.contains(roleId)) {
            config.staffRoleIds.add(roleId)
            log.info { "Added staff role $roleId to guild $guildId" }
        } else {
            log.debug { "Staff role $roleId already exists for guild $guildId" }
        }
        return configRepository.save(config)
    }

    @Transactional
    override fun removeStaffRole(guildId: Long, roleId: String): TicketConfig {
        recordAction("remove_staff_role")
        val config = getOrCreateConfig(guildId)
        config.staffRoleIds.remove(roleId)
        log.info { "Removed staff role $roleId from guild $guildId" }
        return configRepository.save(config)
    }

    override fun isStaffRole(guildId: Long, roleId: String): Boolean {
        return getConfig(guildId)?.staffRoleIds?.contains(roleId) ?: false
    }

    override fun getStaffRoles(guildId: Long): List<String> {
        return getConfig(guildId)?.staffRoleIds ?: emptyList()
    }

    @Transactional
    override fun addCategory(guildId: Long, category: TicketCategory): TicketConfig {
        recordAction("add_category")
        val config = getOrCreateConfig(guildId)
        category.config = config
        categoryRepository.save(category)
        log.info { "Added category '${category.name}' to guild $guildId" }
        return config
    }

    @Transactional
    override fun removeCategory(guildId: Long, categoryId: Long): TicketConfig {
        recordAction("remove_category")
        val config = getOrCreateConfig(guildId)
        val category = categoryRepository.findByIdOrNull(categoryId)
        if (category != null && category.config?.id == config.id) {
            categoryRepository.delete(category)
            log.info { "Removed category '${category.name}' from guild $guildId" }
        }
        return config
    }

    override fun getCategories(guildId: Long): List<TicketCategory> {
        val config = getConfig(guildId) ?: return emptyList()
        return categoryRepository.findByConfigOrderByDisplayOrderAsc(config)
    }
}
