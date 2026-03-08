package ru.sablebot.common.worker.modules.audit.service

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class ActionsHolderService {
    private val leaveNotifiedCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.SECONDS)
        .build<String, Boolean>()

    private val messageDeletedCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, Boolean>()

    fun isLeaveNotified(
        guildId: Long,
        userId: Long,
    ): Boolean = leaveNotifiedCache.getIfPresent(getMemberKey(guildId, userId)) == true

    fun setLeaveNotified(
        guildId: Long,
        userId: Long,
    ) = leaveNotifiedCache.put(getMemberKey(guildId, userId), true)

    private fun getMemberKey(
        guildId: Long,
        userId: Long,
    ): String = "${guildId}_${userId}"
}