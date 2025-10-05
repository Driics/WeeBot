package ru.sablebot.common.worker.command.model.dsl

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import ru.sablebot.common.model.CommandCategory
import java.util.*

fun slashCommand(
    name: String,
    description: String,
    category: CommandCategory,
    uniqueId: UUID,
    block: SlashCommandDeclarationBuilder.() -> Unit
) = SlashCommandDeclarationBuilder(
    name,
    description,
    category,
    uniqueId,
).apply(block)

@InteractionsDsl
class SlashCommandDeclarationBuilder(
    val name: String,
    val description: String,
    val category: CommandCategory,
    val uniqueId: UUID
) {
    var nsfw = false
    var examples: List<String>? = null
    var executor: SlashCommandExecutor? = null
    var botPermissions: Set<Permission>? = null
    var defaultMemberPermissions: DefaultMemberPermissions? = null
    val subcommands = mutableListOf<SlashCommandDeclarationBuilder>()
    val subcommandGroups = mutableListOf<SlashCommandGroupDeclarationBuilder>()

    fun subcommand(
        name: String,
        description: String,
        uniqueId: UUID,
        block: SlashCommandDeclarationBuilder.() -> Unit
    ) {
        subcommands.add(
            SlashCommandDeclarationBuilder(
                name,
                description,
                category,
                uniqueId
            ).apply(block)
        )
    }

    fun subcommandGroup(
        name: String,
        description: String,
        block: SlashCommandGroupDeclarationBuilder.() -> Unit
    ) {
        subcommandGroups.add(
            SlashCommandGroupDeclarationBuilder(
                name,
                description,
                category
            ).apply(block)
        )
    }

    fun build(): SlashCommandDeclaration =
        SlashCommandDeclaration(
            name,
            description,
            nsfw,
            category,
            uniqueId,
            examples,
            botPermissions ?: emptySet(),
            defaultMemberPermissions,
            executor,
            subcommands.map { it.build() },
            subcommandGroups.map { it.build() }
        )
}

@InteractionsDsl
class SlashCommandGroupDeclarationBuilder(
    val name: String,
    val description: String,
    val category: CommandCategory,
) {
    val subcommands = mutableListOf<SlashCommandDeclarationBuilder>()

    fun subcommand(
        name: String,
        description: String,
        uniqueId: UUID,
        block: SlashCommandDeclarationBuilder.() -> (Unit)
    ) {
        subcommands += SlashCommandDeclarationBuilder(
            name,
            description,
            category,
            uniqueId
        ).apply(block)
    }

    fun build(): SlashCommandGroupDeclaration =
        SlashCommandGroupDeclaration(
            name,
            description,
            category,
            subcommands.map { it.build() }
        )
}