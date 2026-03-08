package ru.sablebot.module.moderation.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.sablebot.common.persistence.entity.ModerationCase

interface IModerationService {
    suspend fun ban(guild: Guild, target: Member, moderator: Member, reason: String?, duration: Long?, deleteDays: Int?): ModerationCase
    suspend fun unban(guild: Guild, targetUser: User, moderator: Member, reason: String?): ModerationCase
    suspend fun kick(guild: Guild, target: Member, moderator: Member, reason: String?): ModerationCase
    suspend fun warn(guild: Guild, target: Member, moderator: Member, reason: String): ModerationCase
    suspend fun timeout(guild: Guild, target: Member, moderator: Member, duration: Long, reason: String?): ModerationCase
    suspend fun removeTimeout(guild: Guild, target: Member, moderator: Member, reason: String?): ModerationCase
    suspend fun purgeMessages(channel: TextChannel, count: Int, filterUser: User?): Int
    suspend fun lockChannel(channel: TextChannel, moderator: Member, reason: String?)
    suspend fun unlockChannel(channel: TextChannel, moderator: Member)
    fun getWarnings(guildId: Long, targetId: String): List<ModerationCase>
    fun clearWarnings(guildId: Long, targetId: String): Int
    fun getCase(guildId: Long, caseNumber: Int): ModerationCase?
    fun getModLog(guildId: Long, targetId: String): List<ModerationCase>
}
