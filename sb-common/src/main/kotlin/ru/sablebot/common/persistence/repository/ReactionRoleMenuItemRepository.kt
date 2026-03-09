package ru.sablebot.common.persistence.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.ReactionRoleMenuItem
import ru.sablebot.common.persistence.repository.base.GuildRepository

@Repository
interface ReactionRoleMenuItemRepository : GuildRepository<ReactionRoleMenuItem> {

    fun findByMenuIdOrderByDisplayOrderAsc(menuId: Long): List<ReactionRoleMenuItem>

    fun findByMenuIdAndActive(menuId: Long, active: Boolean): List<ReactionRoleMenuItem>

    fun findByMenuIdAndActiveOrderByDisplayOrderAsc(menuId: Long, active: Boolean): List<ReactionRoleMenuItem>

    fun findByGroupId(groupId: Long): List<ReactionRoleMenuItem>

    fun findByGroupIdAndActive(groupId: Long, active: Boolean): List<ReactionRoleMenuItem>

    fun findByGuildIdAndRoleId(guildId: Long, roleId: String): List<ReactionRoleMenuItem>

    fun findAllByGuildId(guildId: Long): List<ReactionRoleMenuItem>

    fun findByGuildId(guildId: Long, pageable: Pageable): Page<ReactionRoleMenuItem>

    fun countByMenuId(menuId: Long): Int

    fun countByMenuIdAndActive(menuId: Long, active: Boolean): Int

    fun countByGroupId(groupId: Long): Int

    fun existsByMenuIdAndRoleId(menuId: Long, roleId: String): Boolean

    fun deleteByMenuId(menuId: Long)
}
