package ru.sablebot.common.worker.message.model

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.dv8tion.jda.api.entities.IMentionable
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.message.model.modals.ModalArguments
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@Component
class InteractivityManager {
    companion object {
        val DELAY = 5.minutes
        private const val MAX_SIZE = 100L
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @PreDestroy
    fun shutdown() {
        scope.cancel("InteractivityManager shutdown")
    }

    val buttonInteractionCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .build<UUID, ButtonInteractionCallback>()
        .asMap()
    val selectMenuInteractionCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .build<UUID, SelectMenuInteractionCallback>()
        .asMap()
    val selectMenuEntityInteractionCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .build<UUID, SelectMenuEntityInteractionCallback>()
        .asMap()
    val modalCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .build<UUID, ModalInteractionCallback>()
        .asMap()

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
    fun entitySelectMenu(
        callbackAlwaysEphemeral: Boolean,
        builder: (EntitySelectMenu.Builder).() -> (Unit) = {},
        callback: suspend (ComponentContext, List<IMentionable>) -> (Unit)
    ): EntitySelectMenu {
        val buttonId = UUID.randomUUID()
        selectMenuEntityInteractionCallbacks[buttonId] =
            SelectMenuEntityInteractionCallback(callbackAlwaysEphemeral, callback)
        return EntitySelectMenu.create(
            UnleashedComponentId(buttonId).toString(),
            listOf(EntitySelectMenu.SelectTarget.CHANNEL)
        )
            .apply(builder)
            .build()
    }

    class JDAButtonBuilder(internal var button: Button) {
        // https://youtrack.jetbrains.com/issue/KT-6519
        @get:JvmSynthetic // Hide from Java callers
        var emoji: Emoji
            @Deprecated("", level = DeprecationLevel.ERROR) // Prevent Kotlin callers
            get() = throw UnsupportedOperationException()
            set(value) {
                button = button.withEmoji(value)
            }

        var disabled
            get() = button.isDisabled
            set(value) {
                this.button = button.withDisabled(value)
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
        val callback: suspend (ComponentContext, ModalArguments) -> (Unit)
    )
}