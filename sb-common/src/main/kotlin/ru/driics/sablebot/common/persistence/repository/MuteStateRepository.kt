package ru.driics.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.driics.sablebot.common.persistence.entity.MuteState
import ru.driics.sablebot.common.persistence.repository.base.MemberRepository

@Repository
interface MuteStateRepository : MemberRepository<MuteState> {
    fun deleteByGuildIdAndUserIdAndChannelId(guildId: Long, userId: String, channelId: String)
}