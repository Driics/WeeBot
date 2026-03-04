package ru.sablebot.api.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.sablebot.api.dto.event.ModerationCommandEvent
import ru.sablebot.api.dto.moderation.*
import ru.sablebot.common.model.ModerationCaseType
import ru.sablebot.common.persistence.repository.ModerationCaseRepository

@Service
class ModerationApiService(
    private val moderationCaseRepository: ModerationCaseRepository,
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    private val logger = KotlinLogging.logger {}

    @Transactional(readOnly = true)
    fun getCases(guildId: Long, page: Int, size: Int, actionType: ModerationCaseType?): CaseListResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "caseNumber"))
        val casesPage = if (actionType != null) {
            moderationCaseRepository.findByGuildIdAndActionType(guildId, actionType, pageable)
        } else {
            moderationCaseRepository.findByGuildId(guildId, pageable)
        }

        return CaseListResponse(
            cases = casesPage.content.map { it.toCaseResponse() },
            total = casesPage.totalElements,
            page = page,
            size = size
        )
    }

    @Transactional(readOnly = true)
    fun getCase(guildId: Long, caseNumber: Int): CaseResponse? {
        return moderationCaseRepository.findByGuildIdAndCaseNumber(guildId, caseNumber)?.toCaseResponse()
    }

    fun requestBan(guildId: Long, request: BanRequest, moderatorId: String, moderatorName: String) {
        val event = ModerationCommandEvent(
            type = "BAN",
            guildId = guildId.toString(),
            targetId = request.targetId,
            reason = request.reason,
            duration = request.duration,
            deleteMessageDays = request.deleteMessageDays,
            moderatorId = moderatorId,
            moderatorName = moderatorName
        )
        kafkaTemplate.send("sablebot.commands.moderation", guildId.toString(), event)
        logger.info { "Ban request sent for guild $guildId, target ${request.targetId}" }
    }

    fun requestKick(guildId: Long, request: KickRequest, moderatorId: String, moderatorName: String) {
        val event = ModerationCommandEvent(
            type = "KICK",
            guildId = guildId.toString(),
            targetId = request.targetId,
            reason = request.reason,
            moderatorId = moderatorId,
            moderatorName = moderatorName
        )
        kafkaTemplate.send("sablebot.commands.moderation", guildId.toString(), event)
        logger.info { "Kick request sent for guild $guildId, target ${request.targetId}" }
    }

    fun requestWarn(guildId: Long, request: WarnRequest, moderatorId: String, moderatorName: String) {
        val event = ModerationCommandEvent(
            type = "WARN",
            guildId = guildId.toString(),
            targetId = request.targetId,
            reason = request.reason,
            moderatorId = moderatorId,
            moderatorName = moderatorName
        )
        kafkaTemplate.send("sablebot.commands.moderation", guildId.toString(), event)
        logger.info { "Warn request sent for guild $guildId, target ${request.targetId}" }
    }

    private fun ru.sablebot.common.persistence.entity.ModerationCase.toCaseResponse() = CaseResponse(
        id = id!!,
        caseNumber = caseNumber,
        actionType = actionType,
        moderatorId = moderatorId,
        moderatorName = moderatorName,
        targetId = targetId,
        targetName = targetName,
        reason = reason,
        duration = duration,
        createdAt = createdAt,
        active = active
    )
}
