package ru.driics.sablebot.common.worker.configuration

import org.quartz.spi.TriggerFiredBundle
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.scheduling.quartz.SpringBeanJobFactory
import javax.sql.DataSource

@Configuration
open class QuartzConfiguration(
    private val dataSource: DataSource,
    private val applicationContext: ApplicationContext
) : SchedulerFactoryBeanCustomizer {
    @Bean
    open fun jobFactory(): SpringBeanJobFactory {
        return object : SpringBeanJobFactory() {
            override fun createJobInstance(bundle: TriggerFiredBundle): Any {
                val job = super.createJobInstance(bundle)
                applicationContext.autowireCapableBeanFactory.autowireBean(job)
                return job
            }
        }
    }

    override fun customize(schedulerFactoryBean: SchedulerFactoryBean) {
        schedulerFactoryBean.apply {
            setJobFactory(jobFactory())
            setDataSource(dataSource)
        }
    }
}