package ru.sablebot.common.worker.command.util

import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Generates deterministic UUIDs for Discord commands based on command paths.
 *
 * Uses UUID v5 (SHA-1 based) with a DNS namespace to ensure the same command path
 * always produces the same UUID across deployments. This eliminates the need for
 * hardcoded UUID strings in command declarations.
 *
 * Command path format: Fully qualified name (e.g., "moderation.ban", "music.play", "ping")
 */
@Component
class CommandUuidGenerator {
    companion object {
        // DNS namespace UUID (standard UUID v5 namespace)
        private val NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
    }

    /**
     * Generate a deterministic UUID from a command path.
     *
     * @param commandPath The fully qualified command path (e.g., "music.play")
     * @return A UUID that will be identical for the same command path
     */
    fun generateUuid(commandPath: String): UUID {
        return UUID.nameUUIDFromBytes(
            (NAMESPACE.toString() + commandPath).toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * Generate a deterministic UUID for a command using its category and name.
     *
     * @param category The command's category (e.g., MUSIC, MODERATION, GENERAL)
     * @param commandName The command's name (e.g., "play", "ping", "ban")
     * @return A UUID that will be identical for the same category and command name
     */
    fun generate(category: CommandCategory, commandName: String): UUID {
        val commandPath = "${category.name.lowercase()}.${commandName.lowercase()}"
        return generateUuid(commandPath)
    }
}
