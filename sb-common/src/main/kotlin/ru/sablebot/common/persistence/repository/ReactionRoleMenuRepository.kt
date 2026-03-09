package ru.sablebot.common.persistence.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import ru.sablebot.common.model.ReactionRoleMenuType
import ru.sablebot.common.persistence.entity.ReactionRoleMenu
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface ReactionRoleMenuRepository : GuildRepository<ReactionRoleMenu> {

    fun findByChannelIdAndMessageId(channelId: String, messageId: String): ReactionRoleMenu?

    fun findByGuildIdAndActive(guildId: Long, active: Boolean): List<ReactionRoleMenu>

    fun findByGuildIdAndMenuType(guildId: Long, menuType: ReactionRoleMenuType): List<ReactionRoleMenu>

    fun findByGuildIdAndMenuTypeAndActive(
        guildId: Long,
        menuType: ReactionRoleMenuType,
        active: Boolean
    ): List<ReactionRoleMenu>

    fun findAllByGuildId(guildId: Long): List<ReactionRoleMenu>

    fun findByGuildId(guildId: Long, pageable: Pageable): Page<ReactionRoleMenu>

    fun existsByChannelIdAndMessageId(channelId: String, messageId: String): Boolean

    fun countByGuildIdAndActive(guildId: Long, active: Boolean): Int
}
