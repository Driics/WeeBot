package ru.driics.sablebot.common.worker.shared.support

import net.dv8tion.jda.api.JDA
import org.springframework.jmx.export.annotation.ManagedAttribute
import org.springframework.jmx.export.annotation.ManagedResource
import ru.driics.sablebot.common.support.jmx.JmxNamedResource
import java.util.concurrent.ScheduledThreadPoolExecutor

@ManagedResource
class JmxJDAMBean(private val jda: JDA) : JmxNamedResource {

    private val rateLimitPool: ScheduledThreadPoolExecutor? =
        jda.rateLimitPool as? ScheduledThreadPoolExecutor

    /* =====================================================
                             COMMON
       ===================================================== */

    @ManagedAttribute(description = "Returns the ping of shard")
    fun getGatewayPing(): Long = jda.gatewayPing

    @ManagedAttribute(description = "Returns the status of shard")
    fun getStatus(): String = jda.status.name

    @ManagedAttribute(description = "Returns the total amount of JSON responses that discord has sent.")
    fun getResponseTotal(): Long = jda.responseTotal

    @ManagedAttribute(description = "Returns the guild count handled by this shard")
    fun getGuildCount(): Long = jda.guildCache.size()

    @ManagedAttribute(description = "Returns the text channels count handled by this shard")
    fun getTextChannelsCount(): Long = jda.textChannelCache.size()

    @ManagedAttribute(description = "Returns the voice channels count handled by this shard")
    fun getVoiceChannelCount(): Long = jda.voiceChannelCache.size()

    /* =====================================================
                         RATE LIMIT POOL
       ===================================================== */

    @ManagedAttribute(description = "[Rate-Limit Pool] Returns the number of threads that execute tasks")
    fun getRatePoolActiveCount(): Int = rateLimitPool?.activeCount ?: 0

    @ManagedAttribute(description = "[Rate-Limit Pool] Return the current pool size")
    fun getRatePoolSize(): Int = rateLimitPool?.poolSize ?: 0

    @ManagedAttribute(description = "[Rate-Limit Pool] Returns the size of the core pool of threads")
    fun getRateCorePoolSize(): Int = rateLimitPool?.corePoolSize ?: 0

    @ManagedAttribute(description = "[Rate-Limit Pool] Sets the core size of the pool")
    fun setRateCorePoolSize(corePoolSize: Int) {
        rateLimitPool?.corePoolSize = corePoolSize
    }

    @ManagedAttribute(description = "[Rate-Limit Pool] Returns the max size allowed in the pool of threads")
    fun getRateMaxPoolSize(): Int = rateLimitPool?.maximumPoolSize ?: 0

    @ManagedAttribute(description = "[Rate-Limit Pool] Sets the max size allowed in the pool of threads")
    fun setRateMaxPoolSize(maxPoolSize: Int) {
        rateLimitPool?.maximumPoolSize = maxPoolSize
    }

    @ManagedAttribute(description = "[Rate-Limit Pool] Returns the total number of completed tasks")
    fun getRatePoolCompletedTaskCount(): Long = rateLimitPool?.completedTaskCount ?: 0

    @ManagedAttribute(description = "[Rate-Limit Pool] Returns the largest number of threads that have been in the pool")
    fun getRateLargestPoolSize(): Int = rateLimitPool?.largestPoolSize ?: 0

    @ManagedAttribute(description = "[Rate-Limit Pool] Returns the size of current queue")
    fun getRatePoolQueueSize(): Int = rateLimitPool?.queue?.size ?: 0

    @ManagedAttribute(description = "[Rate-Limit Pool] Returns the number of additional elements that this queue can accept without blocking")
    fun getRatePoolQueueRemainingCapacity(): Int = rateLimitPool?.queue?.remainingCapacity() ?: 0

    @ManagedAttribute(description = "[Rate-Limit Pool] Returns the total number of tasks that have ever been scheduled for execution")
    fun getRatePoolTaskCount(): Long = rateLimitPool?.taskCount ?: 0

    override val jmxName: String = jda.shardInfo.toString()
}