package ru.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.sablebot.common.persistence.entity.TicketCategory
import ru.sablebot.common.persistence.entity.TicketConfig

@Repository
interface TicketCategoryRepository : JpaRepository<TicketCategory, Long> {

    fun findByConfig(config: TicketConfig): List<TicketCategory>

    fun findByConfigAndEnabledOrderByDisplayOrderAsc(config: TicketConfig, enabled: Boolean): List<TicketCategory>

    fun findByConfigOrderByDisplayOrderAsc(config: TicketConfig): List<TicketCategory>

    fun findByConfigAndName(config: TicketConfig, name: String): TicketCategory?
}
