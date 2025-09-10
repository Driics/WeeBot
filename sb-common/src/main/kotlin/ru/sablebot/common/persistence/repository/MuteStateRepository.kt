package ru.sablebot.common.persistence.repository

import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.MuteState
import ru.sablebot.common.persistence.repository.base.MemberRepository

@Repository
interface MuteStateRepository : MemberRepository<MuteState> {
    fun deleteByGuildIdAndUserIdAndChannelId(guildId: Long, userId: String, channelId: String)
}