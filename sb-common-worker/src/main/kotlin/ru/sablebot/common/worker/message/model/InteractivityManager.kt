package ru.sablebot.common.worker.message.model

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Scheduler
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.message.model.modals.ModalArguments
import ru.sablebot.common.worker.message.model.modals.ModalContext
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@Component
class InteractivityManager {
    companion object {
        val DELAY = 5.minutes
        private const val MAX_SIZE = 100L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("InteractivityManager"))

    @PreDestroy
    fun shutdown() {
        scope.cancel("InteractivityManager shutdown")
    }

    val buttonInteractionCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .scheduler(Scheduler.systemScheduler())
        .removalListener<UUID, ButtonInteractionCallback> { key, _, cause ->
            // TODO: meter removal events (key, cause)
        }
        .build<UUID, ButtonInteractionCallback>()
        .asMap()
    val selectMenuInteractionCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .scheduler(Scheduler.systemScheduler())
        .removalListener<UUID, SelectMenuInteractionCallback> { key, _, cause ->
            // TODO: meter removal events (key, cause)
        }
        .build<UUID, SelectMenuInteractionCallback>()
        .asMap()
    val selectMenuEntityInteractionCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .scheduler(Scheduler.systemScheduler())
        .removalListener<UUID, SelectMenuEntityInteractionCallback> { key, _, cause ->
            // TODO: meter removal events (key, cause)
        }
        .build<UUID, SelectMenuEntityInteractionCallback>()
        .asMap()
    val modalCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .scheduler(Scheduler.systemScheduler())
        .removalListener<UUID, ModalInteractionCallback> { key, _, cause ->
            // TODO: meter removal events (key, cause)
        }
        .build<UUID, ModalInteractionCallback>()
        .asMap()

    /**
     * Creates an interactive button
     */
    fun buttonForUser(
        targetUser: User,
        callbackAlwaysEphemeral: Boolean,
        style: ButtonStyle,
        label: String = "",
        builder: (JDAButtonBuilder).() -> (Unit) = {},
        callback: suspend (ComponentContext) -> (Unit)
    ) = buttonForUser(targetUser.idLong, callbackAlwaysEphemeral, style, label, builder, callback)

    /**
     * Creates an interactive button, the ID in the [button] will be replaced with a [UnleashedComponentId]
     */
    fun buttonForUser(
        targetUser: User,
        callbackAlwaysEphemeral: Boolean,
        button: Button,
        callback: suspend (ComponentContext) -> (Unit)
    ) = buttonForUser(targetUser.idLong, callbackAlwaysEphemeral, button, callback)

    /**
     * Creates an interactive button
     */
    fun buttonForUser(
        targetUserId: Long,
        callbackAlwaysEphemeral: Boolean,
        style: ButtonStyle,
        label: String = "",
        builder: (JDAButtonBuilder).() -> (Unit) = {},
        callback: suspend (ComponentContext) -> (Unit)
    ) = button(
        callbackAlwaysEphemeral,
        style,
        label,
        builder
    ) {
        if (targetUserId != it.user.idLong) {
            it.reply(true) {
                styled(
                    "Wo are you?",
                    "??"
                )
            }
            return@button
        }

        callback.invoke(it)
    }

    /**
     * Creates an interactive button, the ID in the [button] will be replaced with a [UnleashedComponentId]
     */
    fun buttonForUser(
        targetUserId: Long,
        callbackAlwaysEphemeral: Boolean,
        button: Button,
        callback: suspend (ComponentContext) -> (Unit)
    ) = button(
        callbackAlwaysEphemeral,
        button
    ) {
        if (targetUserId != it.user.idLong) {
            it.reply(true) {
                styled(
                    "Wo are you?",
                    "??"
                )
            }
            return@button
        }

        callback.invoke(it)
    }

    /**
     * Creates an interactive button
     */
    fun button(
        callbackAlwaysEphemeral: Boolean,
        style: ButtonStyle,
        label: String = "",
        builder: (JDAButtonBuilder).() -> (Unit) = {},
        callback: suspend (ComponentContext) -> (Unit)
    ) = button(
        callbackAlwaysEphemeral,
        UnleashedButton.of(style, label, null)
            .let {
                JDAButtonBuilder(it).apply(builder).button
            },
        callback
    )

    /**
     * Creates an interactive button, the ID in the [button] will be replaced with a [UnleashedComponentId]
     */
    fun button(
        callbackAlwaysEphemeral: Boolean,
        button: Button,
        callback: suspend (ComponentContext) -> (Unit)
    ): Button {
        val buttonId = UUID.randomUUID()
        buttonInteractionCallbacks[buttonId] = ButtonInteractionCallback(callbackAlwaysEphemeral, callback)
        return button
            .withId(UnleashedComponentId(buttonId).toString())
    }

    /**
     * Creates an interactive select menu
     */
    fun stringSelectMenu(
        callbackAlwaysEphemeral: Boolean,
        builder: (StringSelectMenu.Builder).() -> (Unit) = {},
        callback: suspend (ComponentContext, List<String>) -> (Unit)
    ): StringSelectMenu {
        val menuId = UUID.randomUUID()
        selectMenuInteractionCallbacks[menuId] = SelectMenuInteractionCallback(callbackAlwaysEphemeral, callback)
        return StringSelectMenu.create(UnleashedComponentId(menuId).toString())
            .apply(builder)
            .build()
    }

    /**
     * Creates an interactive select menu
     */
    fun entitySelectMenu(
        callbackAlwaysEphemeral: Boolean,
        builder: (EntitySelectMenu.Builder).() -> (Unit) = {},
        callback: suspend (ComponentContext, List<IMentionable>) -> (Unit)
    ): EntitySelectMenu {
        val menuId = UUID.randomUUID()
        selectMenuEntityInteractionCallbacks[menuId] =
            SelectMenuEntityInteractionCallback(callbackAlwaysEphemeral, callback)
        return EntitySelectMenu.create(
            UnleashedComponentId(menuId).toString(),
            listOf(EntitySelectMenu.SelectTarget.CHANNEL)
        )
            .apply(builder)
            .build()
    }

    class JDAButtonBuilder(internal var button: Button) {
        fun emoji(value: Emoji) {
            button = button.withEmoji(value)
        }

        var disabled: Boolean
            get() = button.isDisabled
            set(value) {
                button = button.withDisabled(value)
            }
    }


    data class ButtonInteractionCallback(
        /**
         * If true, the created context will always be ephemeral
         */
        val alwaysEphemeral: Boolean,
        val callback: suspend (ComponentContext) -> (Unit)
    )

    data class SelectMenuInteractionCallback(
        /**
         * If true, the created context will always be ephemeral
         */
        val alwaysEphemeral: Boolean,
        val callback: suspend (ComponentContext, List<String>) -> (Unit)
    )

    data class SelectMenuEntityInteractionCallback(
        /**
         * If true, the created context will always be ephemeral
         */
        val alwaysEphemeral: Boolean,
        val callback: suspend (ComponentContext, List<IMentionable>) -> (Unit)
    )

    data class ModalInteractionCallback(

        /**
         * If true, the created context will always be ephemeral
         */
        val alwaysEphemeral: Boolean,
        val callback: suspend (ModalContext, ModalArguments) -> (Unit)
    )
}