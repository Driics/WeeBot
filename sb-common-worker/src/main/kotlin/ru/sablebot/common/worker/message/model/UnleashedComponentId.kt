package ru.sablebot.common.worker.message.model

import java.util.*

class UnleashedComponentId(val uniqueId: UUID) {
    companion object {
        const val UNLEASHED_COMPONENT_PREFIX = "unleashed"

        operator fun invoke(componentIdWithPrefix: String): UnleashedComponentId {
            require(componentIdWithPrefix.startsWith("$UNLEASHED_COMPONENT_PREFIX:")) { "Not a Unleashed Component ID!" }
            return UnleashedComponentId(UUID.fromString(componentIdWithPrefix.substringAfterLast(":")))
        }
    }

    override fun toString() = "$UNLEASHED_COMPONENT_PREFIX:$uniqueId"
}