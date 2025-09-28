package ru.sablebot.common.worker.message.model

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.dv8tion.jda.api.entities.IMentionable
import org.springframework.stereotype.Component
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
    /*val modalCallbacks = Caffeine
        .newBuilder()
        .maximumSize(MAX_SIZE)
        .expireAfterWrite(DELAY.toJavaDuration())
        .build<UUID, ModalInteractionCallback>()
        .asMap()*/


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
    )/*

    data class ModalInteractionCallback(
        */
    /**
         * If true, the created context will always be ephemeral
     *//*
        val alwaysEphemeral: Boolean,
        val callback: suspend (ModalContext, ModalArguments) -> (Unit)
    )*/
}