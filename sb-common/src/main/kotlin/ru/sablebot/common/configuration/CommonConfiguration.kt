package ru.sablebot.common.configuration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.*
import org.springframework.core.Ordered
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.TaskUtils
import org.springframework.transaction.annotation.EnableTransactionManagement
import ru.sablebot.common.support.SbCacheManager
import ru.sablebot.common.support.SbCacheManagerImpl
import ru.sablebot.common.support.jmx.ThreadPoolTaskExecutorMBean
import java.util.concurrent.ThreadPoolExecutor

@EnableAsync
@EnableRetry(order = Ordered.HIGHEST_PRECEDENCE)
@EnableScheduling
@EnableAspectJAutoProxy
@EnableTransactionManagement
@EntityScan("ru.sablebot")
@ComponentScan("ru.sablebot")
@ConfigurationPropertiesScan("ru.sablebot")
@EnableJpaRepositories("ru.sablebot")
@Import(MBeanConfiguration::class, RabbitConfiguration::class)
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
            name = "Spring TaskExecutor",
            taskExecutor = executor
        )

    @Bean(EXECUTOR)
    @Primary
    fun taskExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = commonProperties.execution.corePoolSize
        maxPoolSize = commonProperties.execution.maxPoolSize
        queueCapacity = commonProperties.execution.queueCapacity
        setThreadNamePrefix(EXECUTOR)
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(30)
        setAllowCoreThreadTimeOut(true)
        initialize()
    }

    @Bean(SCHEDULER)
    fun taskScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = commonProperties.execution.schedulerPoolSize
        isRemoveOnCancelPolicy = true
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(30)
        setThreadNamePrefix(SCHEDULER)
        setErrorHandler(TaskUtils.LOG_AND_SUPPRESS_ERROR_HANDLER)
    }

    @Bean("sbCacheManager")
    @Primary
    @ConditionalOnMissingBean(SbCacheManager::class)
    fun sbCacheManager(): SbCacheManager = SbCacheManagerImpl()
}