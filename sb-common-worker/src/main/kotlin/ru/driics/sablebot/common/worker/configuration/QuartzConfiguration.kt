package ru.driics.sablebot.common.worker.configuration

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.scheduling.quartz.SpringBeanJobFactory
import javax.sql.DataSource

@Configuration
open class QuartzConfiguration(
    private val dataSource: DataSource
) : SchedulerFactoryBeanCustomizer {
    @Bean
    open fun jobFactory() = SpringBeanJobFactory()

    override fun customize(schedulerFactoryBean: SchedulerFactoryBean?) {
        schedulerFactoryBean?.apply {
            setJobFactory(jobFactory())
            setDataSource(dataSource)
        }
    }
}