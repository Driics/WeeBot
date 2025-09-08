package ru.driics.sablebot.common.configuration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.*
import org.springframework.core.Ordered
import org.springframework.core.task.TaskExecutor
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import ru.driics.sablebot.common.support.SbCacheManager
import ru.driics.sablebot.common.support.SbCacheManagerImpl
import ru.driics.sablebot.common.support.jmx.ThreadPoolTaskExecutorMBean

@EnableAsync
@EnableRetry(order = Ordered.HIGHEST_PRECEDENCE)
@EnableScheduling
@EntityScan("ru.driics")
@ComponentScan("ru.driics")
@Import(MBeanConfiguration::class)
@Configuration
class CommonConfiguration @Autowired constructor(
    private val commonProperties: CommonProperties
) {
    companion object {
        const val SCHEDULER = "taskScheduler"
        const val EXECUTOR = "taskExecutor"
    }

    @Bean
    fun taskExecutorMBean(executor: ThreadPoolTaskExecutor) =
        ThreadPoolTaskExecutorMBean(
            taskExecutor = executor,
            objectName = "Spring TaskExecutor"
        )

    @Bean(EXECUTOR)
    @Primary
    fun taskExecutor(): TaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = commonProperties.execution.corePoolSize
        maxPoolSize = commonProperties.execution.maxPoolSize
        setThreadNamePrefix(EXECUTOR)
    }

    @Bean(SCHEDULER)
    fun taskScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = commonProperties.execution.schedulerPoolSize
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(30)
        setThreadNamePrefix(EXECUTOR)
    }

    @Bean("sbCacheManager")
    @Primary
    fun sbCacheManager(): SbCacheManager = SbCacheManagerImpl()
}