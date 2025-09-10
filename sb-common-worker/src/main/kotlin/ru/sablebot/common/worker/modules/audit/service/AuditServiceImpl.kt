package ru.sablebot.common.worker.modules.audit.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.persistence.entity.AuditAction
import ru.sablebot.common.persistence.repository.AuditActionRepository
import ru.sablebot.common.service.AuditConfigService
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.feature.service.FeatureSetService
import ru.sablebot.common.worker.modules.audit.model.AuditActionBuilder
import ru.sablebot.common.worker.modules.audit.provider.AuditForwardProvider
import ru.sablebot.common.worker.modules.audit.provider.ForwardProvider
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Service
open class AuditServiceImpl(
    private val workerProperties: WorkerProperties,
    private val actionRepository: AuditActionRepository,
    private val configService: AuditConfigService,
    private val featureSetService: FeatureSetService,
    forwardProviderList: List<AuditForwardProvider> = emptyList()
) : AuditService {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    private val forwardProviders: Map<AuditActionType, AuditForwardProvider> =
        forwardProviderList
            .mapNotNull { provider ->
                val annotation = provider::class.java.getAnnotation(ForwardProvider::class.java)
                annotation?.value?.let { it to provider }
            }.toMap()


    override fun save(action: AuditAction, attachments: Map<String, ByteArray>): AuditAction {
        val config = configService.getByGuildId(action.guildId)
        if (config != null && config.enabled) {
            var savedAction = action
            if (featureSetService.isAvailable(action.guildId)) {
                savedAction = actionRepository.save(savedAction)
            }
            if (forwardProviders.isNotEmpty()) {
                val provider = forwardProviders[savedAction.actionType]
                if (provider != null) {
                    if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        TransactionSynchronizationManager.registerSynchronization(
                            object : TransactionSynchronization {
                                override fun afterCommit() {
                                    provider.send(config, savedAction, attachments)
                                }
                            }
                        )
                    } else {
                        provider.send(config, savedAction, attachments)
                    }
                }
            }

            return savedAction
        }
        return action
    }

    @Transactional
    @Scheduled(cron = "0 0 0 1 * ?")
    override fun runCleanUp() = runCleanUp(workerProperties.audit.keepMonths)

    @Transactional
    override fun runCleanUp(durationMonths: Int) {
        require(durationMonths > 0) { "durationMonths must be > 0" }
        log.info { "Starting audit cleanup for $durationMonths months old" }
        val thresholdDate: Date = Date.from(
            LocalDateTime.now(ZoneId.of("UTC"))
                .minusMonths(durationMonths.toLong())
                .atZone(ZoneId.of("UTC"))
                .toInstant()
        )
        actionRepository.deleteByActionDateBefore(thresholdDate)
        log.info { "Audit cleanup completed" }
    }

    override fun log(guildId: Long, type: AuditActionType): AuditActionBuilder =
        object : AuditActionBuilder(guildId, type) {
            @Transactional
            override fun save(): AuditAction = this@AuditServiceImpl.save(this.action, attachments)
        }
}