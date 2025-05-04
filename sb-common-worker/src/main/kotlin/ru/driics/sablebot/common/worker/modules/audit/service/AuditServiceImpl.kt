package ru.driics.sablebot.common.worker.modules.audit.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.driics.sablebot.common.model.AuditActionType
import ru.driics.sablebot.common.persistence.repository.AuditActionRepository
import ru.driics.sablebot.common.worker.configuration.WorkerProperties
import ru.driics.sablebot.common.worker.modules.audit.provider.AuditForwardProvider
import ru.driics.sablebot.common.worker.modules.audit.provider.ForwardProvider
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Service
class AuditServiceImpl(
    private val workerProperties: WorkerProperties,
    private val actionRepository: AuditActionRepository,
    @Autowired(required = false) forwardProviderList: List<AuditForwardProvider>?
) : AuditService {
    private val log = KotlinLogging.logger { }
    private val forwardProviders: Map<AuditActionType, AuditForwardProvider> =
        forwardProviderList
            ?.mapNotNull { provider ->
                val annotation = provider::class.java.getAnnotation(ForwardProvider::class.java)
                annotation?.value?.let { it to provider }
            }
            ?.toMap()
            .orEmpty()


    @Transactional
    @Scheduled(cron = "0 0 0 1 * ?")
    override fun runCleanUp() = runCleanUp(workerProperties.audit.keepMonths)

    override fun runCleanUp(durationMonths: Int) {
        log.info { "Starting audit cleanup for $durationMonths months old" }
        val thresholdDate: Date = Date.from(
            LocalDateTime.now()
                .minusMonths(durationMonths.toLong())
                .atZone(ZoneId.systemDefault())
                .toInstant()
        )
        actionRepository.deleteByActionDateBefore(thresholdDate)
        log.info { "Audit cleanup completed" }
    }
}