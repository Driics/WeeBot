package ru.driics.sablebot.common.service

import org.hibernate.StaleStateException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional


@Service
open class TransactionHandlerImpl : TransactionHandler {

    @Transactional(propagation = Propagation.REQUIRED)
    override fun runInTransaction(action: () -> Unit) = action.invoke()

    @Transactional(propagation = Propagation.REQUIRED)
    override fun <T> runInTransaction(action: () -> T): T = action.invoke()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun runInNewTransaction(action: () -> Unit) = action.invoke()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun <T> runInNewTransaction(action: () -> T): T = action.invoke()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun runWithLockRetry(action: () -> Unit) = action.invoke()

    @Retryable(StaleStateException::class, maxAttempts = 5, backoff = Backoff(delay = 500))
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun <T> runWithLockRetry(action: () -> T): T = action.invoke()
}
