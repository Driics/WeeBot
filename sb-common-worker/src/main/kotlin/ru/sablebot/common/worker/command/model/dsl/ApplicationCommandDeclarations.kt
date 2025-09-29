package ru.sablebot.common.worker.command.model.dsl

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import ru.sablebot.common.model.CommandCategory
import java.util.*

sealed class ExecutableApplicationCommandDeclaration {
    abstract val name: String
    abstract val category: CommandCategory
    abstract val uniqueId: UUID
}

data class SlashCommandDeclaration(
    override val name: String,
    val description: String,
    override val category: CommandCategory,
    override val uniqueId: UUID,
    val examples: List<String>?,
    val botPermissions: Set<Permission>,
    val defaultMemberPermissions: DefaultMemberPermissions?,
    val executor: SlashCommandExecutor?,
    val subcommands: List<SlashCommandDeclaration>,
    val subcommandGroups: List<SlashCommandGroupDeclaration>
) : ExecutableApplicationCommandDeclaration()

data class SlashCommandGroupDeclaration(
    val name: String,
    val description: String,
    val category: CommandCategory,
    val subcommands: List<SlashCommandDeclaration>
)

open data class LegacyCommandDeclaration(

)