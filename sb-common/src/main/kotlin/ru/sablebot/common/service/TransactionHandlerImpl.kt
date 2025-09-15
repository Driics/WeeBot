package ru.sablebot.common.service

import org.hibernate.StaleStateException
import org.springframework.dao.CannotAcquireLockException
import org.springframework.dao.DeadlockLoserDataAccessException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.dao.PessimisticLockingFailureException
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

    @Retryable(
        include = [
            StaleStateException::class,
            OptimisticLockingFailureException::class,
            PessimisticLockingFailureException::class,
            CannotAcquireLockException::class,
            DeadlockLoserDataAccessException::class,
        ],
        maxAttempts = 5,
        backoff = Backoff(delay = 500, multiplier = 2.0, maxDelay = 5_000)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun runWithLockRetry(action: () -> Unit) = action.invoke()

    @Retryable(
        include = [
            StaleStateException::class,
            OptimisticLockingFailureException::class,
            PessimisticLockingFailureException::class,
            CannotAcquireLockException::class,
            DeadlockLoserDataAccessException::class,
        ],
        maxAttempts = 5,
        backoff = Backoff(delay = 500, multiplier = 2.0, maxDelay = 5_000)
    )
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun <T> runWithLockRetry(action: () -> T): T = action.invoke()
}
