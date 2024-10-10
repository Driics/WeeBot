package ru.driics.sablebot.common.worker.shared.service

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.driics.sablebot.common.worker.configuration.WorkerProperties

@Service
class SupportServiceImpl @Autowired constructor(
    private val discordService: DiscordService,
    private val workerProperties: WorkerProperties
) : SupportService {
    override val supportGuild: Guild?
        get() {
            val guildId = workerProperties.support.guildId

            if (guildId == 0L || !discordService.isConnected(guildId)) {
                return null
            }

            return discordService.shardManager.getGuildById(guildId)
        }

    override val donatorRole: Role?
        get() {
            val donatorRoleId = workerProperties.support.donatorRoleId

            if (donatorRoleId == 0L) {
                return null
            }

            return supportGuild?.getRoleById(donatorRoleId)
        }

    override fun Member.isModerator(): Boolean {
        val moderatorRoleId = workerProperties.support.moderatorRoleId

        if (moderatorRoleId == 0L) {
            return false
        }

        val supportGuild = supportGuild ?: return false

        if (supportGuild != this.guild) return false

        val moderatorRole = supportGuild.getRoleById(moderatorRoleId)
        return moderatorRole != null && this.roles.contains(moderatorRole)
    }

    override fun grantDonators(donatorIds: Set<String>) {
        if (donatorIds.isEmpty()) return

        if (donatorRole == null) return

        val guild = donatorRole!!.guild

        if (guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            donatorIds.mapNotNull(guild::getMemberById)
                .filter { it.roles.none { role -> role == donatorRole } }
                .forEach { guild.addRoleToMember(it, donatorRole!!).queue() }
        }
    }
}