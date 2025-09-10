package ru.sablebot.common.support.jmx

import org.springframework.jmx.export.annotation.ManagedAttribute
import org.springframework.jmx.export.annotation.ManagedResource
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@ManagedResource
class ThreadPoolTaskExecutorMBean(
    private val taskExecutor: ThreadPoolTaskExecutor,
    private val objectName: String = taskExecutor.javaClass.simpleName
) : JmxNamedResource {
    init {
        require(objectName.isNotBlank()) { "Object name cannot be blank" }
    }

    override val jmxName: String
        get() = objectName

    @ManagedAttribute(description = "Returns the number of active threads executing tasks")
    fun getActiveCount(): Int = taskExecutor.activeCount

    @ManagedAttribute(description = "Returns the current pool size")
    fun getPoolSize(): Int = taskExecutor.poolSize

    @ManagedAttribute(description = "Returns the core pool size of threads")
    fun getCorePoolSize(): Int = taskExecutor.corePoolSize

    @ManagedAttribute(description = "Sets the core pool size")
    fun setCorePoolSize(corePoolSize: Int) {
        taskExecutor.corePoolSize = corePoolSize
    }

    @ManagedAttribute(description = "Returns the maximum pool size")
    fun getMaxPoolSize(): Int = taskExecutor.maxPoolSize

    @ManagedAttribute(description = "Sets the maximum pool size")
    fun setMaxPoolSize(maxPoolSize: Int) {
        taskExecutor.maxPoolSize = maxPoolSize
    }

    @ManagedAttribute(description = "Returns the keep-alive time in seconds")
    fun getKeepAliveSeconds(): Int = taskExecutor.keepAliveSeconds

    @ManagedAttribute(description = "Sets the keep-alive time in seconds")
    fun setKeepAliveSeconds(keepAliveSeconds: Int) {
        taskExecutor.keepAliveSeconds = keepAliveSeconds
    }

    @ManagedAttribute(description = "Sets the queue capacity for tasks")
    fun setQueueCapacity(queueCapacity: Int) {
        taskExecutor.queueCapacity = queueCapacity
    }

    @ManagedAttribute(description = "Allow or disallow core thread timeouts")
    fun setAllowCoreThreadTimeout(allowCoreThreadTimeout: Boolean) {
        taskExecutor.setAllowCoreThreadTimeOut(allowCoreThreadTimeout)
    }

    @ManagedAttribute(description = "Returns the total number of completed tasks")
    fun getCompletedTaskCount(): Long = taskExecutor.threadPoolExecutor.completedTaskCount

    @ManagedAttribute(description = "Returns the largest pool size reached")
    fun getLargestPoolSize(): Int = taskExecutor.threadPoolExecutor.largestPoolSize

    @ManagedAttribute(description = "Returns the current queue size")
    fun getQueueSize(): Int = taskExecutor.threadPoolExecutor.queue.size

    @ManagedAttribute(description = "Returns the remaining queue capacity")
    fun getQueueRemainingCapacity(): Int = taskExecutor.threadPoolExecutor.queue.remainingCapacity()

    @ManagedAttribute(description = "Returns the total number of tasks scheduled")
    fun getTaskCount(): Long = taskExecutor.threadPoolExecutor.taskCount

}