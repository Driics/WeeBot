package ru.sablebot.common.worker.command.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class CommandCoolDownManager(
    private val meterRegistry: MeterRegistry,
    private val cooldownMs: Long = 3000L
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(cooldownMs, TimeUnit.MILLISECONDS)
        .build<String, Long>()

    private fun key(userId: Long, commandName: String) = "$userId:$commandName"

    fun isOnCooldown(userId: Long, commandName: String): Boolean {
        val lastUsage = cache.getIfPresent(key(userId, commandName)) ?: return false
        val remaining = cooldownMs - (System.currentTimeMillis() - lastUsage)
        if (remaining > 0) {
            meterRegistry.counter("sablebot.commands.cooldown.hits", "command", commandName).increment()
            return true
        }
        return false
    }

    fun recordUsage(userId: Long, commandName: String) {
        cache.put(key(userId, commandName), System.currentTimeMillis())
    }

    fun getRemainingCooldown(userId: Long, commandName: String): Long {
        val lastUsage = cache.getIfPresent(key(userId, commandName)) ?: return 0
        val remaining = cooldownMs - (System.currentTimeMillis() - lastUsage)
        return if (remaining > 0) remaining else 0
    }
}
