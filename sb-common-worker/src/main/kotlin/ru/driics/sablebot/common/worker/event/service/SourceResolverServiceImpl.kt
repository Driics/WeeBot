package ru.driics.sablebot.common.worker.event.service

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import org.springframework.stereotype.Service
import org.springframework.util.ReflectionUtils
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

@Service
class SourceResolverServiceImpl : SourceResolverService {

    // Thread-safe lazy initialization with concurrent maps for better performance
    private val guildAccessors = ConcurrentHashMap<Class<out GenericEvent>, Method?>()
    private val userAccessors = ConcurrentHashMap<Class<out GenericEvent>, Method?>()

    override fun getGuild(event: GenericEvent?): Guild? {
        return event?.let { safeEvent ->
            val method = guildAccessors.computeIfAbsent(safeEvent::class.java) { clazz ->
                clazz.findMethod("getGuild")
            }
            method?.invokeMethodSafely<Guild>(safeEvent)
        }
    }

    override fun getUser(event: GenericEvent?): User? {
        return getAuthor(event) as? User
    }

    override fun getMember(event: GenericEvent?): Member? {
        return getAuthor(event) as? Member
    }

    private fun getAuthor(event: GenericEvent?): Any? {
        return event?.let { safeEvent ->
            val method = userAccessors.computeIfAbsent(safeEvent::class.java) { clazz ->
                clazz.findMethod("getUser") ?: clazz.findMethod("getAuthor")
            }
            method?.invokeMethodSafely<Any>(safeEvent)
        }
    }

    // Extension functions for cleaner method resolution and invocation
    private fun Class<*>.findMethod(methodName: String): Method? {
        return runCatching {
            ReflectionUtils.findMethod(this, methodName)
        }.getOrNull()
    }

    private inline fun <reified T> Method.invokeMethodSafely(target: Any): T? {
        return runCatching {
            ReflectionUtils.invokeMethod(this, target) as? T
        }.getOrNull()
    }
}