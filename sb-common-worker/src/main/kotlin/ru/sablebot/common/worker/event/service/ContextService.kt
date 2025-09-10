package ru.sablebot.common.worker.event.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.requests.RestAction
import java.awt.Color
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * Central service that manages an application-scoped contextual state ("execution context")
 * primarily bound to a Discord guild (server) plus optional user, locale and color information.
 *
 * Responsibilities:
 * 1. Locale management (per guild or transient override).
 * 2. Color management (brand / embed color associated with a context execution).
 * 3. Guild / User context initialization from multiple sources (IDs, JDA events, entities).
 * 4. Synchronous and suspend (coroutine) safe contextual execution helpers.
 * 5. Preservation & restoration of context snapshots (useful for scheduling / async boundaries).
 * 6. Flow / concurrency helpers that keep guild context across reactive style pipelines.
 *
 * Typical usage (synchronous):
 * ```kotlin
 * contextService.withContext(guildId) {
 *     // Code here sees guildId, locale and color bound to the current thread (e.g. MDC / ThreadLocal).
 * }
 * ```
 *
 * Typical usage (coroutine):
 * ```kotlin
 * contextService.withGuildContext(guildId) {
 *     val locale = getLocale()
 *     // Launch suspend operations still preserving contextual metadata.
 * }
 * ```
 *
 * Executing multiple guild operations concurrently:
 * ```kotlin
 * val results = contextService.withMultipleGuildContexts(listOf(1L, 2L, 3L)) { id ->
 *     // Each lambda call runs with its own guild context
 *     computeStatsFor(id)
 * }
 * ```
 *
 * Applying context to a Flow pipeline:
 * ```kotlin
 * someFlow
 *     .let { flow -> contextService.run { flow.withGuildContext(guildId) } }
 *     .collect { value -> /* context active here */ }
 * ```
 */
interface ContextService {

    // ---------------------------- Locale management ----------------------------

    /** Sets (or clears if null) the current contextual locale. */
    fun setLocale(locale: Locale?)
    /** Returns the active locale (may fall back to a default locale if none was set). */
    fun getLocale(): Locale
    /** Resolves a locale by name / tag (with graceful fallback). */
    fun getLocale(localeName: String): Locale
    /** Resolves the locale associated with the provided guild entity. */
    fun getLocale(guild: Guild): Locale
    /** Resolves the locale associated with a guild ID. */
    fun getLocale(guildId: Long): Locale

    // ---------------------------- Color management -----------------------------

    /** Assigns (or clears) the current contextual color (e.g. embed accent color). */
    fun setColor(color: Color?)
    /** Returns the color set in current context if any. */
    fun getColor(): Color?
    /** Returns application default color (might be null if not configured). */
    fun getDefaultColor(): Color?

    // ------------------------- Context initialization --------------------------

    /** Initializes context from a generic JDA event (extracting guild / user if available). */
    fun initContext(event: GenericEvent)
    /** Initializes context from a resolved guild entity. */
    fun initContext(guild: Guild)
    /** Initializes context derived from a user. */
    fun initContext(user: User)
    /** Initializes context from a raw guild ID. */
    fun initContext(guildId: Long)
    /** Clears all contextual state (guild / locale / color / user). */
    fun resetContext()

    // ---------------------- Synchronous context execution ----------------------

    /**
     * Executes [action] while temporarily binding the provided [guildId] as context.
     * The previous context (if any) is restored after completion.
     */
    fun <T> withContext(guildId: Long?, action: () -> T): T
    /** Variant accepting a Nullable [Guild] entity. */
    fun <T> withContext(guild: Guild?, action: () -> T): T
    /** Executes a side-effecting action under a guild ID context (result-less variant). */
    fun withContext(guildId: Long?, action: () -> Unit)
    /** Executes a side-effecting action under a guild entity context (result-less variant). */
    fun withContext(guild: Guild?, action: () -> Unit)

    // --------------------- Asynchronous (suspend) execution --------------------

    /**
     * Executes a suspend [action] binding the given [guild] for the duration.
     * Ensures context is restored even if an exception is thrown.
     */
    suspend fun withContextAsync(guild: Guild?, action: suspend () -> Unit)

    // -------------------------- Discord queue helpers --------------------------

    /**
     * Queues a JDA [RestAction] ensuring the proper guild context is applied during callbacks.
     * @param guild Optional guild associated with the action (improves logging / localization).
     * @param action The JDA RestAction to queue.
     * @param success Success callback executed under the same contextual guild.
     */
    fun <T> queue(guild: Guild?, action: RestAction<T>, success: (T) -> Unit,  failure: (Throwable) -> Unit = {})
    /** Same as [queue] but uses a raw [guildId]. */
    fun <T> queue(guildId: Long?, action: RestAction<T>, success: (T) -> Unit,  failure: (Throwable) -> Unit = {})

    // -------------------- Extended coroutine / Flow features -------------------

    /**
     * Runs a suspend [action] in a new coroutine context while binding a guild context.
     * This is the coroutine-friendly counterpart of the synchronous [withContext].
     *
     * Example:
     * ```kotlin
     * contextService.withGuildContext(guildId) {
     *     val loc = getLocale()
     *     performLocalizedWork(loc)
     * }
     * ```
     *
     * @param guildId Guild ID to bind (or null to clear existing one for the scope).
     * @param context Additional coroutine context (default: [kotlinx.coroutines.Dispatchers.Default]).
     * @param action Suspend block executed with context active.
     * @return Result of [action].
     */
    suspend fun <T> withGuildContext(
        guildId: Long?,
        context: CoroutineContext = kotlinx.coroutines.Dispatchers.Default,
        action: suspend CoroutineScope.() -> T
    ): T

    /**
     * Executes [action] for each guild ID concurrently, preserving ordering of results.
     * Each invocation receives a properly established guild context.
     *
     * @param guildIds List of guild IDs to process.
     * @param action Per-guild suspend transformer.
     * @return Results in the same order as [guildIds].
     */
    suspend fun <T> withMultipleGuildContexts(
        guildIds: List<Long>,
        action: suspend (Long) -> T
    ): List<T>

    /**
     * Extension that re-emits a Flow while preserving a stable guild context for the entire stream.
     * Use this when upstream operators might shift threads.
     *
     * Example:
     * ```kotlin
     * myRepository.events()
     *   .let { flow -> contextService.run { flow.withGuildContext(guildId) } }
     *   .collect { /* context visible */ }
     * ```
     */
    fun <T> Flow<T>.withGuildContext(guildId: Long?): Flow<T>

    // --------------------- Context state / utility helpers ---------------------

    /** Returns true if a guild context is currently active. */
    fun hasGuildContext(): Boolean = try {
        getCurrentGuildId() != null
    } catch (_: Exception) { false }

    /** Retrieves currently bound guild ID or null if not set. */
    fun getCurrentGuildId(): Long?

    /** Returns current user ID bound in contextual state (if any). */
    fun getCurrentUserId(): String?

    /**
     * Captures current context and executes [action]; safe across thread boundaries.
     * Commonly used for executor services not aware of application context.
     */
    fun <T> preserveContext(action: () -> T): T

    /**
     * Temporarily overrides context properties, executes [action] and restores the previous state.
     * Only non-null arguments are applied.
     */
    fun <T> withTemporaryContext(
        guildId: Long? = null,
        locale: Locale? = null,
        color: Color? = null,
        userId: String? = null,
        action: () -> T
    ): T

    /** Batch update for selective context fields (null values ignored). */
    fun updateContext(
        guildId: Long? = null,
        locale: Locale? = null,
        color: Color? = null,
        userId: String? = null
    )

    /** Creates an immutable snapshot of the current contextual state. */
    fun createContextSnapshot(): ContextSnapshot

    /** Restores a previously created [ContextSnapshot]. */
    fun restoreContextSnapshot(snapshot: ContextSnapshot)
}

/**
 * Immutable representation of a captured contextual state which can later be restored.
 * Useful for scheduling, deferred execution or bridging across asynchronous boundaries.
 */
data class ContextSnapshot(
    val guildId: Long?,
    val locale: Locale?,
    val color: Color?,
    val userId: String?
)

// -------------------------- Extension utilities -------------------------------

/**
 * Executes suspend [action] with this [Guild]'s ID bound as contextual guild.
 * @see ContextService.withGuildContext
 */
suspend fun <T> Guild.withContext(
    contextService: ContextService,
    action: suspend () -> T
): T = contextService.withGuildContext(this.idLong) { action() }

/**
 * Queues this JDA [RestAction] while ensuring [guild] context is set for callbacks.
 * Simplifies logging and localization inside the success handler.
 */
fun <T> RestAction<T>.queueWithContext(
    contextService: ContextService,
    guild: Guild?,
    success: (T) -> Unit
) = contextService.queue(guild, this, success)

/**
 * Applies guild context to an entire Flow pipeline; call prior to terminal operators.
 */
fun <T> Flow<T>.withGuildContext(
    contextService: ContextService,
    guildId: Long?
): Flow<T> = contextService.run { withGuildContext(guildId) }

/**
 * Maps each element to [R] executing [transform] under the element's derived guild context.
 *
 * NOTE: For large collections consider a structured concurrency variant for parallelism.
 */
suspend fun <T, R> Collection<T>.mapWithContext(
    contextService: ContextService,
    getGuildId: (T) -> Long,
    transform: suspend (T) -> R
): List<R> {
    val out = ArrayList<R>(this.size)
    for (item in this) {
        out += contextService.withGuildContext(getGuildId(item)) { transform(item) }
    }
    return out
}