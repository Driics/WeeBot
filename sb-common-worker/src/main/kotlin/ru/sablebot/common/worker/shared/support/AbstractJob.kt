package ru.sablebot.common.worker.shared.support

import io.github.oshai.kotlinlogging.KotlinLogging
import org.quartz.*
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
        // Ignore non-positive delays
        if (!duration.isPositive()) {
            log.warn { "Skip reschedule for ${context.jobDetail.key}: non-positive duration=$duration" }
            return
        }

        log.info { "Rescheduling job ${context.jobDetail.key} in $duration" }

        val original = context.trigger
        val newTrigger = TriggerBuilder.newTrigger()
            .withIdentity(original.key)
            .forJob(context.jobDetail)
            .usingJobData(JobDataMap(original.jobDataMap))
            .startAt(Date.from(Instant.now().plusMillis(duration.inWholeMilliseconds)))
            .withSchedule(SimpleScheduleBuilder.simpleSchedule())
            .build()

        runCatching {
            schedulerFactoryBean.scheduler.rescheduleJob(
                context.trigger.key,
                newTrigger
            )
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