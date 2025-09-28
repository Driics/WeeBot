package ru.sablebot.common.worker.shared.support

import org.quartz.spi.TriggerFiredBundle
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class SpringBeanJobFactory : org.springframework.scheduling.quartz.SpringBeanJobFactory(), ApplicationContextAware {
    private lateinit var context: ApplicationContext

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.context = applicationContext
    }

    override fun createJobInstance(bundle: TriggerFiredBundle): Any {
        val jobInstance = super.createJobInstance(bundle)
        context.autowireCapableBeanFactory.autowireBean(jobInstance)
        return jobInstance
    }
}