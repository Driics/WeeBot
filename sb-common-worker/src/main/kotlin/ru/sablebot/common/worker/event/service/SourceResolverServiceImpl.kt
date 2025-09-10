package ru.sablebot.common.worker.event.service

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
    private val guildAccessors = ConcurrentHashMap<Class<out GenericEvent>, Method>()
    private val userAccessors = ConcurrentHashMap<Class<out GenericEvent>, Method>()
    private val memberAccessors = ConcurrentHashMap<Class<out GenericEvent>, Method>()

    override fun getGuild(event: GenericEvent?): Guild? {
        return event?.let { safeEvent ->
            val clazz = safeEvent::class.java
            val method = guildAccessors[clazz]
                ?: clazz.findMethod("getGuild")?.also { guildAccessors.putIfAbsent(clazz, it) }
            method?.invokeMethodSafely<Guild>(safeEvent)
        }
    }

    override fun getUser(event: GenericEvent?): User? {
        return getAuthor(event) as? User
    }

    override fun getMember(event: GenericEvent?): Member? {
        return event?.let { safeEvent ->
            val clazz = safeEvent::class.java
            val method = memberAccessors[clazz]
                ?: clazz.findMethod("getMember")?.also { memberAccessors.putIfAbsent(clazz, it) }
            method?.invokeMethodSafely<Member>(safeEvent)
        }
    }

    private fun getAuthor(event: GenericEvent?): Any? {
        return event?.let { safeEvent ->
            val clazz = safeEvent::class.java
            val method = userAccessors[clazz]
                ?: (clazz.findMethod("getUser") ?: clazz.findMethod("getAuthor"))
                    ?.also { userAccessors.putIfAbsent(clazz, it) }
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