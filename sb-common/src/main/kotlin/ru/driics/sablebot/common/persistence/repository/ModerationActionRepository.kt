package ru.driics.sablebot.common.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.driics.sablebot.common.persistence.entity.ModerationAction

@Repository
interface ModerationActionRepository : JpaRepository<ModerationAction, Long>