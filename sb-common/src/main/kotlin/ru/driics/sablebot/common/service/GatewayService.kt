package ru.driics.sablebot.common.service

import ru.driics.sablebot.common.model.request.CheckOwnerRequest
import ru.driics.sablebot.common.model.status.StatusDto

interface GatewayService {
    fun isChannelOwner(req: CheckOwnerRequest): Boolean

    fun getWorkerStatus(): StatusDto

    fun evictCache(cacheName: String, guildId: Long)
}