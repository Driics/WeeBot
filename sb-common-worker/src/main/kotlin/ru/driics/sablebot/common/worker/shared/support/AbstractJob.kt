package ru.driics.sablebot.common.worker.shared.support

import io.github.oshai.kotlinlogging.KotlinLogging
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import kotlin.time.Duration

@Component
abstract class AbstractJob : Job {
    @Autowired
    private lateinit var schedulerFactoryBean: SchedulerFactoryBean

    companion object {
        private val log = KotlinLogging.logger { }
    }

    fun reschedule(
        context: JobExecutionContext,
        duration: Duration
    ) {
        log.info { "Rescheduling job ${context.jobDetail}" }

        val newTrigger = TriggerBuilder.newTrigger()
            .startAt(Date.from(Instant.now().plusMillis(duration.inWholeMilliseconds)))
            .withSchedule(SimpleScheduleBuilder.simpleSchedule())
            .build()

        runCatching {
            schedulerFactoryBean.scheduler.rescheduleJob(context.trigger.key, newTrigger)
        }.onFailure { exception ->
            log.warn { "Could not reschedule job ${context.jobDetail} $exception" }
        }
    }
}

fun JobExecutionContext.rescheduleIn(duration: Duration, job: AbstractJob) {
    job.reschedule(this, duration)
}

class RescheduleBuilder {
    private var duration: Duration = Duration.ZERO

    fun after(duration: Duration): RescheduleBuilder {
        this.duration = duration
        return this
    }

    fun execute(context: JobExecutionContext, job: AbstractJob) {
        job.reschedule(context, duration)
    }
}

fun reschedule(block: RescheduleBuilder.() -> Unit): RescheduleBuilder {
    return RescheduleBuilder().apply(block)
}