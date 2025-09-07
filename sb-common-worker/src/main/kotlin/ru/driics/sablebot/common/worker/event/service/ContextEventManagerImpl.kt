package ru.driics.sablebot.common.worker.event.service

import jakarta.annotation.PreDestroy
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.Event
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.task.TaskRejectedException
import org.springframework.jmx.export.MBeanExportOperations
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import ru.driics.sablebot.common.support.jmx.ThreadPoolTaskExecutorMBean
import ru.driics.sablebot.common.worker.configuration.WorkerProperties
import ru.driics.sablebot.common.worker.event.DiscordEvent
import ru.driics.sablebot.common.worker.event.intercept.EventFilterFactory
import ru.driics.sablebot.common.worker.event.listeners.DiscordEventListener

@Service
class ContextEventManagerImpl @Autowired constructor(
    private val workerProperties: WorkerProperties,
    private val mBeanExportOperations: MBeanExportOperations,
    private val contextService: ContextService
) : SbEventManager {
    private val log = LoggerFactory.getLogger(ContextEventManagerImpl::class.java)

    private val listeners: MutableList<EventListener> = mutableListOf()
    private val filterFactoryMap: MutableMap<Class<*>, EventFilterFactory<*>> = mutableMapOf()
    private val shardExecutors: MutableMap<Int, ThreadPoolTaskExecutor> = mutableMapOf()

    override fun handle(event: GenericEvent) {
        if (!workerProperties.events.asyncExecution) {
            handleEvent(event)
            return
        }

        try {
            getTaskExecutor(event.jda).execute { handleEvent(event) }
        } catch (e: TaskRejectedException) {
            log.debug("Event rejected: {}", event)
        }
    }

    private fun handleEvent(event: GenericEvent) = try {
        loopListeners(event)
    } catch (e: Exception) {
        log.error("Event manager caused an uncaught exception", e)
    } finally {
        contextService.resetContext()
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

    private fun registerListeners(listeners: List<EventListener>) = synchronized(this.listeners) {
        val listenerSet = listeners.toSet() + this.listeners
        this.listeners.clear()
        this.listeners.addAll(listenerSet)
        this.listeners.sortWith(this::compareListeners)
    }

    private fun compareListeners(first: EventListener, second: EventListener): Int =
        getPriority(first) - getPriority(second)

    private fun getPriority(eventListener: EventListener?): Int =
        eventListener?.javaClass?.getAnnotation(DiscordEvent::class.java)?.priority ?: Int.MAX_VALUE

    override fun register(listener: Any) {
        if (listener !is EventListener) {
            throw IllegalArgumentException("Listener must implement EventListener")
        }
        registerListeners(listOf(listener))
    }

    override fun unregister(listener: Any): Unit = synchronized(listeners) {
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
                mBeanExportOperations.registerManagedResource(ThreadPoolTaskExecutorMBean(this, name))
            }
        }

    @PreDestroy
    fun destroy() = shardExecutors.values.forEach(ThreadPoolTaskExecutor::shutdown)

    override fun getRegisteredListeners(): List<Any> = listeners.toList()
}