package ru.sablebot.common.worker.event.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.guild.member.GenericGuildMemberEvent
import net.dv8tion.jda.api.requests.RestAction
import org.slf4j.MDC
import org.springframework.stereotype.Service
import ru.sablebot.common.configuration.CommonProperties
import ru.sablebot.common.service.ConfigService
import ru.sablebot.common.utils.LocaleUtils
import java.awt.Color
import java.util.*
import kotlin.coroutines.CoroutineContext

@Service
open class ContextServiceImpl(
    private val commonProperties: CommonProperties,
    private val configService: ConfigService,
    private val resolverService: SourceResolverService
) : ContextService {
    companion object {
        private const val MDC_GUILD = "guildId"
        private const val MDC_USER = "userId"
        private val logger = KotlinLogging.logger {}
    }

    private data class ContextHolder(
        val locale: Locale? = null,
        val color: Color? = null,
        val guildId: Long? = null,
        val userId: String? = null
    )

    private val localeHolder = ThreadLocal<Locale?>()
    private val colorHolder = ThreadLocal<Color?>()
    private val guildHolder = ThreadLocal<Long?>()

    val accentColor: Color by lazy {
        runCatching { Color.decode(commonProperties.discord.defaultAccentColor) }
            .getOrElse { Color.CYAN }
    }

    override fun getLocale(): Locale =
        localeHolder.get() ?: run {
            guildHolder.get()?.let { guildId ->
                val locale = LocaleUtils.getOrDefault(configService.getLocale(guildId))
                setLocale(locale)
                locale
            }
        } ?: LocaleUtils.getDefaultLocale()

    override fun getColor(): Color? =
        colorHolder.get() ?: run {
            guildHolder.get()?.let { guildId ->
                val colorHex = configService.getColor(guildId)
                runCatching { Color.decode(colorHex) }.getOrNull()
            }
        } ?: getDefaultColor()

    override fun getDefaultColor(): Color? = accentColor

    override fun getLocale(localeName: String): Locale =
        LocaleUtils.getOrDefault(localeName)

    override fun getLocale(guild: Guild): Locale =
        getLocale(guild.idLong)

    override fun getLocale(guildId: Long): Locale =
        LocaleUtils.getOrDefault(configService.getLocale(guildId))

    override fun setLocale(locale: Locale?) {
        if (locale == null) {
            localeHolder.remove()
        } else {
            localeHolder.set(locale)
        }
    }

    override fun setColor(color: Color?) {
        if (color == null) {
            colorHolder.remove()
        } else {
            colorHolder.set(color)
        }
    }

    private fun setGuildId(guildId: Long?) {
        if (guildId == null) {
            guildHolder.remove()
        } else {
            guildHolder.set(guildId)
        }
    }

    override fun initContext(event: GenericEvent) {
        val (guild, user) = when (event) {
            is GenericGuildMemberEvent -> event.member.guild to event.member.user
            is GenericGuildEvent -> event.guild to null
            else -> null to null
        }.let { (guild, user) ->
            // Fallback to resolver service if needed
            val member = resolverService.getMember(event)
            val resolvedGuild = guild ?: member?.guild ?: resolverService.getGuild(event)
            val resolvedUser = user ?: member?.user ?: resolverService.getUser(event)
            resolvedGuild to resolvedUser
        }

        guild?.let { initContext(it) }
        user?.let { initContext(it) }
    }

    override fun initContext(guild: Guild) {
        initContext(guild.idLong)
    }

    override fun <T> withContext(guildId: Long?, action: () -> T): T =
        withContextInternal(guildId, ::initContext, action)

    override fun <T> withContext(guild: Guild?, action: () -> T): T =
        withContextInternal(guild, ::initContext, action)

    override fun withContext(guildId: Long?, action: () -> Unit) {
        withContextInternal(guildId, ::initContext, action)
    }

    override fun withContext(guild: Guild?, action: () -> Unit) {
        withContextInternal(guild, ::initContext, action)
    }

    private inline fun <T, R> withContextInternal(
        value: T?,
        init: (T) -> Unit,
        action: () -> R
    ): R {
        if (value == null) return action()
        val currentContext = getContext()
        return try {
            resetContext()
            init(value)
            action()
        } finally {
            restoreContextSnapshot(
                ContextSnapshot(
                    guildId = currentContext.guildId,
                    locale = currentContext.locale,
                    color = currentContext.color,
                    userId = currentContext.userId
                )
            )
        }
    }

    private suspend inline fun <T, R> withContextInternalSuspend(
        value: T?,
        crossinline init: (T) -> Unit,
        crossinline action: suspend () -> R
    ): R {
        if (value == null) return action()
        val currentContext = getContext()
        return try {
            resetContext()
            init(value)
            action()
        } finally {
            restoreContextSnapshot(
                ContextSnapshot(
                    guildId = currentContext.guildId,
                    locale = currentContext.locale,
                    color = currentContext.color,
                    userId = currentContext.userId
                )
            )
        }
    }

    override suspend fun withContextAsync(guild: Guild?, action: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            withContextInternalSuspend(guild, ::initContext) {
                // Если TransactionHandler поддерживает suspend-функции, используем его напрямую
                // Иначе выполняем action без транзакции или создаем suspend-версию TransactionHandler
                action()
            }
        }
    }

    override fun <T> queue(
        guild: Guild?,
        action: RestAction<T>,
        success: (T) -> Unit,
        failure: (Throwable) -> Unit,
    ) {
        action.queue(
            { result -> withContext(guild) { success(result) }},
            { error -> withContext(guild) { failure(error) }}
        )
    }

    override fun <T> queue(
        guildId: Long?,
        action: RestAction<T>,
        success: (T) -> Unit,
        failure: (Throwable) -> Unit
    ) {
        action.queue(
            { result -> withContext(guildId) { success(result) }},
            { error -> withContext(guildId) { failure(error) }}
        )
    }

    override fun initContext(user: User) {
        MDC.put(MDC_USER, user.id)
    }

    override fun initContext(guildId: Long) {
        MDC.put(MDC_GUILD, guildId.toString())
        guildHolder.set(guildId)
    }

    override fun resetContext() {
        MDC.remove(MDC_GUILD)
        MDC.remove(MDC_USER)
        guildHolder.remove()
        localeHolder.remove()
        colorHolder.remove()
    }

    private fun getContext(): ContextHolder = ContextHolder(
        locale = localeHolder.get(),
        color = colorHolder.get(),
        guildId = guildHolder.get(),
        userId = MDC.get(MDC_USER)
    )

    private fun setContext(holder: ContextHolder) {
        setLocale(holder.locale)
        setColor(holder.color)
        setGuildId(holder.guildId)

        holder.guildId?.let { MDC.put(MDC_GUILD, it.toString()) }
        holder.userId?.let { MDC.put(MDC_USER, it) }
    }

    override suspend fun <T> withGuildContext(
        guildId: Long?,
        context: CoroutineContext,
        action: suspend CoroutineScope.() -> T
    ): T = withContext(context) {
        val currentContext = getContext()
        try {
            resetContext()
            guildId?.let { initContext(it) }
            action()
        } finally {
            setContext(currentContext)
        }
    }

    // Utility for batch context operations
    override suspend fun <T> withMultipleGuildContexts(
        guildIds: List<Long>,
        action: suspend (Long) -> T
    ): List<T> = coroutineScope {
        guildIds.map { guildId ->
            async {
                withGuildContext(guildId) {
                    action(guildId)
                }
            }
        }.awaitAll()
    }

    override fun <T> Flow<T>.withGuildContext(guildId: Long?): Flow<T> = flow {
        // Переносим suspend-обёртку внутрь collect
        this@withGuildContext.collect { value ->
            withContextInternalSuspend(guildId, ::initContext) {
                emit(value)
            }
        }
    }

    override fun getCurrentGuildId(): Long? = guildHolder.get()
    override fun getCurrentUserId(): String? = MDC.get(MDC_USER)

    override fun <T> preserveContext(action: () -> T): T {
        val snapshot = createContextSnapshot()
        return try {
            action()
        } finally {
            // Ensure any modifications inside action do not leak out
            restoreContextSnapshot(snapshot)
        }
    }

    override fun <T> withTemporaryContext(
        guildId: Long?,
        locale: Locale?,
        color: Color?,
        userId: String?,
        action: () -> T
    ): T {
        // Capture current state first
        val snapshot = createContextSnapshot()
        return try {
            // Apply only explicit non-null overrides
            if (guildId != null) initContext(guildId)
            locale?.let { setLocale(it) }
            color?.let { setColor(it) }
            userId?.let { MDC.put(MDC_USER, it) }
            action()
        } finally {
            // Always restore full previous state
            restoreContextSnapshot(snapshot)
        }
    }

    override fun updateContext(
        guildId: Long?,
        locale: Locale?,
        color: Color?,
        userId: String?
    ) {
        guildId?.let { initContext(it) }
        locale?.let { setLocale(it) }
        color?.let { setColor(it) }
        userId?.let { MDC.put(MDC_USER, it) }
    }

    override fun createContextSnapshot(): ContextSnapshot =
        ContextSnapshot(
            guildId = guildHolder.get(),
            locale = localeHolder.get(),
            color = colorHolder.get(),
            userId = MDC.get(MDC_USER)
        )

    override fun restoreContextSnapshot(snapshot: ContextSnapshot) {
        // First, completely reset the current context to ensure clean state
        resetContext()

        // Restore each context property from the snapshot
        snapshot.guildId?.let { guildId ->
            // Use initContext to properly set both ThreadLocal and MDC
            initContext(guildId)
        }

        snapshot.locale?.let { locale ->
            // Set the locale in ThreadLocal
            setLocale(locale)
        }

        snapshot.color?.let { color ->
            // Set the color in ThreadLocal
            setColor(color)
        }

        snapshot.userId?.let { userId ->
            // Set the user ID in MDC (Mapped Diagnostic Context)
            MDC.put(MDC_USER, userId)
        }
    }
}