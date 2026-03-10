package ru.sablebot.module.tickets.service

import net.dv8tion.jda.api.entities.Guild
import ru.sablebot.common.persistence.entity.TicketCategory
import ru.sablebot.common.persistence.entity.TicketConfig

interface ITicketConfigService {
    fun getConfig(guildId: Long): TicketConfig?
    fun getConfig(guild: Guild): TicketConfig?
    fun getOrCreateConfig(guildId: Long): TicketConfig
    fun getOrCreateConfig(guild: Guild): TicketConfig
    fun saveConfig(config: TicketConfig): TicketConfig
    fun isEnabled(guildId: Long): Boolean
    fun enableTickets(guildId: Long): TicketConfig
    fun disableTickets(guildId: Long): TicketConfig
    fun setSupportChannel(guildId: Long, channelId: String?): TicketConfig
    fun setCategoryChannel(guildId: Long, channelId: String?): TicketConfig
    fun setTranscriptChannel(guildId: Long, channelId: String?): TicketConfig
    fun setMaxTicketsPerUser(guildId: Long, max: Int): TicketConfig
    fun setAutoCloseInactiveDays(guildId: Long, days: Int?): TicketConfig
    fun setDmOnClose(guildId: Long, enabled: Boolean): TicketConfig
    fun addStaffRole(guildId: Long, roleId: String): TicketConfig
    fun removeStaffRole(guildId: Long, roleId: String): TicketConfig
    fun isStaffRole(guildId: Long, roleId: String): Boolean
    fun getStaffRoles(guildId: Long): List<String>
    fun addCategory(guildId: Long, category: TicketCategory): TicketConfig
    fun removeCategory(guildId: Long, categoryId: Long): TicketConfig
    fun getCategories(guildId: Long): List<TicketCategory>
}
