package ru.sablebot.common.service

import ru.sablebot.common.model.request.CheckOwnerRequest
import ru.sablebot.common.model.status.StatusDto

interface GatewayService {
    fun isChannelOwner(req: CheckOwnerRequest): Boolean

    fun getWorkerStatus(): StatusDto

    fun evictCache(cacheName: String, guildId: Long)
}