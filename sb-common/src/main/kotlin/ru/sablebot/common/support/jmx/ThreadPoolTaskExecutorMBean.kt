package ru.sablebot.common.support.jmx

import org.springframework.jmx.export.annotation.ManagedAttribute
import org.springframework.jmx.export.annotation.ManagedResource
import org.springframework.lang.NonNull
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@ManagedResource
class ThreadPoolTaskExecutorMBean(
    val name: String? = null,
    @field:NonNull private val taskExecutor: ThreadPoolTaskExecutor,
) : JmxNamedResource {

    private val objectName: String =
        if (!name.isNullOrBlank()) name else this::class.simpleName ?: "ThreadPoolTaskExecutorMBean"

    @ManagedAttribute(description = "Returns the number of threads that execute tasks")
    fun getActiveCount(): Int = taskExecutor.activeCount

    @ManagedAttribute(description = "Return the current pool size")
    fun getPoolSize(): Int = taskExecutor.poolSize

    @ManagedAttribute(description = "Returns the size of the core pool of threads")
    fun getCorePoolSize(): Int = taskExecutor.corePoolSize

    @ManagedAttribute(description = "Sets the core size of the pool")
    fun setCorePoolSize(corePoolSize: Int) {
        taskExecutor.corePoolSize = corePoolSize
    }

    @ManagedAttribute(description = "Returns the max size allowed in the pool of threads")
    fun getMaxPoolSize(): Int = taskExecutor.maxPoolSize

    @ManagedAttribute(description = "Sets the max size allowed in the pool of threads")
    fun setMaxPoolSize(maxPoolSize: Int) {
        taskExecutor.maxPoolSize = maxPoolSize
    }

    @ManagedAttribute(description = "Returns the number of keep-alive seconds")
    fun getKeepAliveSeconds(): Int = taskExecutor.keepAliveSeconds

    @ManagedAttribute(description = "Sets the keep-alive seconds size in the pool of threads")
    fun setKeepAliveSeconds(keepAliveSeconds: Int) {
        taskExecutor.setKeepAliveSeconds(keepAliveSeconds)
    }

    @ManagedAttribute(description = "Sets the queue capacity the pool of threads")
    fun setQueueCapacity(queueCapacity: Int) {
        taskExecutor.queueCapacity = queueCapacity
    }

    @ManagedAttribute(description = "Allow core thread time-out")
    fun setAllowCoreThreadTimeOut(allowCoreThreadTimeOut: Boolean) {
        taskExecutor.setAllowCoreThreadTimeOut(allowCoreThreadTimeOut)
    }

    @ManagedAttribute(description = "Returns the total number of completed tasks")
    fun getCompletedTaskCount(): Long =
        taskExecutor.threadPoolExecutor.completedTaskCount

    @ManagedAttribute(description = "Returns the largest number of threads that have been in the pool")
    fun getLargestPoolSize(): Int =
        taskExecutor.threadPoolExecutor.largestPoolSize

    @ManagedAttribute(description = "Returns the size of current queue")
    fun getQueueSize(): Int =
        taskExecutor.threadPoolExecutor.queue.size

    @ManagedAttribute(
        description = "Returns the number of additional elements that this queue can accept without blocking"
    )
    fun getQueueRemainingCapacity(): Int =
        taskExecutor.threadPoolExecutor.queue.remainingCapacity()

    @ManagedAttribute(description = "Returns the total number of tasks that have ever been scheduled for execution")
    fun getTaskCount(): Long =
        taskExecutor.threadPoolExecutor.taskCount

    override val jmxName: String
        get() = objectName
}
