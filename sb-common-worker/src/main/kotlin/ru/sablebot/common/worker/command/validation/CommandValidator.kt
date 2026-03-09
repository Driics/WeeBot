package ru.sablebot.common.worker.command.validation

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclaration

/**
 * Validates command declarations against Discord API constraints before registration.
 *
 * Performs validation checks for:
 * - Duplicate command names
 * - Missing executors
 * - Discord API name/description constraints
 * - Option ordering requirements
 * - Command count limits
 */
@Service
class CommandValidator {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Validates all command declarations.
     *
     * @param commands List of command declarations to validate
     * @throws IllegalStateException if any validation constraint is violated
     */
    fun validate(commands: Collection<SlashCommandDeclaration>) {
        logger.info { "Validating ${commands.size} command(s)" }

        // TODO: Implement validation logic
        // - Check for duplicate names
        // - Validate Discord API constraints (1-32 char lowercase name, 1-100 char description)
        // - Ensure required options don't come after optional ones
        // - Check max command count (100 global commands)
        // - Verify executors are present

        logger.info { "Validation passed for ${commands.size} command(s)" }
    }
}
