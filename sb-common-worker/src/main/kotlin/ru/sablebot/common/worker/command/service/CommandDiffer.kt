package ru.sablebot.common.worker.command.service

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.stereotype.Service

/**
 * Compares local command definitions with Discord-registered commands to detect differences.
 *
 * Provides diff computation to:
 * - Identify commands to add (in local but not in Discord)
 * - Identify commands to remove (in Discord but not in local)
 * - Identify commands to update (same name but different options/description)
 * - Skip unnecessary Discord API calls when no changes detected
 */
@Service
class CommandDiffer {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Represents the result of comparing local vs. Discord commands.
     */
    data class CommandDiff(
        val toAdd: List<SlashCommandData>,
        val toUpdate: List<SlashCommandData>,
        val toRemove: List<Command>,
        val unchanged: List<String>
    ) {
        fun isEmpty(): Boolean = toAdd.isEmpty() && toUpdate.isEmpty() && toRemove.isEmpty()
    }

    /**
     * Computes the difference between local and Discord-registered commands.
     *
     * @param localCommands Command definitions from the application
     * @param remoteCommands Commands currently registered in Discord
     * @return CommandDiff containing commands to add, update, remove, and unchanged
     */
    fun computeDiff(
        localCommands: List<SlashCommandData>,
        remoteCommands: List<Command>
    ): CommandDiff {
        logger.debug { "Computing diff between ${localCommands.size} local and ${remoteCommands.size} remote commands" }

        // TODO: Implement diff computation logic
        // - Build maps by command name for efficient lookup
        // - Compare local vs remote to find additions
        // - Compare remote vs local to find removals
        // - For matching names, compare descriptions and options to find updates
        // - Track unchanged commands

        // Stub implementation - assumes no changes
        return CommandDiff(
            toAdd = emptyList(),
            toUpdate = emptyList(),
            toRemove = emptyList(),
            unchanged = localCommands.map { it.name }
        )
    }
}
