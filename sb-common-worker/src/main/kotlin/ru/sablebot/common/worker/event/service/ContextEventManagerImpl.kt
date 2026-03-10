package ru.sablebot.common.worker.event.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import jakarta.annotation.PreDestroy
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.task.TaskRejectedException
import org.springframework.jmx.export.MBeanExportOperations
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import ru.sablebot.common.support.jmx.ThreadPoolTaskExecutorMBean
import ru.sablebot.common.worker.configuration.WorkerProperties
import ru.sablebot.common.worker.event.DiscordEvent
import ru.sablebot.common.worker.event.intercept.EventFilterFactory
import ru.sablebot.common.worker.event.listeners.DiscordEventListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class ContextEventManagerImpl @Autowired constructor(
    private val workerProperties: WorkerProperties,
    private val mBeanExportOperations: MBeanExportOperations,
    private val contextService: ContextService,
    private val meterRegistry: MeterRegistry,
    private val observationRegistry: ObservationRegistry
) : SbEventManager {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val EVENTS_PROCESSED = "sablebot.events.processed"
        private const val EVENTS_REJECTED = "sablebot.events.rejected"
        private const val EVENTS_DURATION = "sablebot.events.duration"
        private const val EVENTS_ERRORS = "sablebot.events.errors"
    }

    private val listeners: MutableList<EventListener> = CopyOnWriteArrayList()
    private val filterFactoryMap: MutableMap<Class<*>, EventFilterFactory<*>> = ConcurrentHashMap()
    private val shardExecutors: MutableMap<Int, ThreadPoolTaskExecutor> = ConcurrentHashMap()

    override fun handle(event: GenericEvent) {
        val eventType = event::class.simpleName ?: "Unknown"

        if (!workerProperties.events.asyncExecution) {
            handleEvent(event, eventType)
            return
        }

        try {
            getTaskExecutor(event.jda).execute { handleEvent(event, eventType) }
        } catch (e: TaskRejectedException) {
            log.debug("Event rejected: {}", event)
            meterRegistry.counter(EVENTS_REJECTED, "event_type", eventType).increment()
        }
    }

    private fun handleEvent(event: GenericEvent, eventType: String) {
        val sample = Timer.start(meterRegistry)
        val observation = Observation.createNotStarted("discord.event", observationRegistry)
            .lowCardinalityKeyValue("event_type", eventType)
            .start()
        val scope = observation.openScope()
        MDC.put("eventType", event.javaClass.simpleName)
        try {
            loopListeners(event)
            meterRegistry.counter(EVENTS_PROCESSED, "event_type", eventType).increment()
        } catch (e: Exception) {
            observation.error(e)
            log.error("Event manager caused an uncaught exception", e)
            meterRegistry.counter(
                EVENTS_ERRORS,
                "event_type",
                eventType,
                "error_type",
                e::class.simpleName ?: "Unknown"
            ).increment()
        } finally {
            sample.stop(meterRegistry.timer(EVENTS_DURATION, "event_type", eventType))
            MDC.remove("eventType")
            scope.close()
            observation.stop()
            contextService.resetContext()
        }
    }

    private fun loopListeners(event: GenericEvent) {
        if (event is SlashCommandInteractionEvent) {
            dispatchChain(SlashCommandInteractionEvent::class.java, event)
        }

        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: ObjectOptimisticLockingFailureException) {
                log.warn(
                    "[{}] optimistic lock happened for {}#{} while handling {}",
                    listener.javaClass.simpleName,
                    e.persistentClassName,
                    e.identifier,
                    event
                )
            } catch (throwable: Throwable) {
                log.error(
                    "[{}] had an uncaught exception for handling {}",
                    listener::class.simpleName,
                    event,
                    throwable
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Event> dispatchChain(type: Class<T>, event: T) {
        val factory = filterFactoryMap[type] as? EventFilterFactory<T> ?: return
        val chain = factory.createChain(event) ?: return

        try {
            chain.doFilter(event)
        } catch (e: Exception) {
            log.error("Could not process chain", e)
        }
    }

    @Autowired
    fun registerContext(listeners: List<DiscordEventListener>) = registerListeners(listeners)

    private fun registerListeners(listeners: List<EventListener>) {
        val listenerSet = listeners.toSet() + this.listeners
        this.listeners.clear()
        this.listeners.addAll(listenerSet)
        this.listeners.sortWith(this::compareListeners)
    }

    private fun compareListeners(first: EventListener, second: EventListener): Int =
        getPriority(first).compareTo(getPriority(second))

    private fun getPriority(eventListener: EventListener?): Int =
        eventListener?.javaClass?.getAnnotation(DiscordEvent::class.java)?.priority ?: Int.MAX_VALUE

    override fun register(listener: Any) {
        if (listener !is EventListener) {
            throw IllegalArgumentException("Listener must implement EventListener")
        }
        registerListeners(listOf(listener))
    }

    override fun unregister(listener: Any) {
        listeners.remove(listener)
    }

    @Autowired
    fun registerFilterFactories(factories: List<EventFilterFactory<*>>) {
        if (factories.isNotEmpty()) {
            factories.forEach { filterFactoryMap.putIfAbsent(it.getType(), it) }
        }
    }

    private fun getTaskExecutor(shard: JDA): ThreadPoolTaskExecutor =
        shardExecutors.computeIfAbsent(shard.shardInfo.shardId) {
            val name = "${shard.shardInfo} Event-Executor"
            ThreadPoolTaskExecutor().apply {
                corePoolSize = workerProperties.events.corePoolSize
                maxPoolSize = workerProperties.events.maxPoolSize
                setBeanName(name)
                setThreadNamePrefix(name)
                initialize()
                mBeanExportOperations.registerManagedResource(ThreadPoolTaskExecutorMBean(name, this))
                ExecutorServiceMetrics.monitor(
                    meterRegistry,
                    this.threadPoolExecutor,
                    "sablebot.event.executor",
                    Tag.of("shard", shard.shardInfo.shardId.toString())
                )
            }
        }

    @PreDestroy
    fun destroy() = shardExecutors.values.forEach(ThreadPoolTaskExecutor::shutdown)

    override fun getRegisteredListeners(): List<Any> = listeners.toList()
}