package ru.driics.sablebot.common.service

interface TransactionHandler {

    fun runInTransaction(action: () -> Unit)

    fun runInNewTransaction(action: () -> Unit)

    fun runWithLockRetry(action: () -> Unit)

    fun <T> runInTransaction(action: () -> T): T

    fun <T> runInNewTransaction(action: () -> T): T

    fun <T> runWithLockRetry(action: () -> T): T
}