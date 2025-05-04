package ru.driics.sablebot.common.worker.modules.moderation.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import ru.driics.sablebot.common.worker.modules.moderation.model.ModerationActionRequest

interface MuteService {

    fun getMutedRole(guild: Guild): Role

    fun mute(request: ModerationActionRequest): Boolean

    fun unmute(author: Member, channel: TextChannel, member: Member): Boolean

    fun refreshMute(member: Member)

    fun isMuted(member: Member, channel: TextChannel): Boolean

    fun clearState(guildId: Long, userId: String, channelId: String)
}

