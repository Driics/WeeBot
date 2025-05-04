package ru.driics.sablebot.common.worker.modules.audit.service

import net.dv8tion.jda.api.entities.Guild
import ru.driics.sablebot.common.model.AuditActionType
import ru.driics.sablebot.common.persistence.entity.AuditAction

interface AuditService {
    fun runCleanUp()
    fun runCleanUp(durationMonths: Int)
    fun save(action: AuditAction, attachments: Map<String, Array<Byte>>): AuditAction
    fun log(guildId: Long, type: AuditActionType) // TODO: AuditActionBuilder
    fun log(guild: Guild, type: AuditActionType) /*TODO return AuditActionBuilder */ {
        log(guild.idLong, type)
    }
}