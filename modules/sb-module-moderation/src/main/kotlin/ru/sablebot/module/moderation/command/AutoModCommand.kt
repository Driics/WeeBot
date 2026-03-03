package ru.sablebot.module.moderation.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.AutoModActionType
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.LinkFilterMode
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.common.worker.message.model.commands.options.StringDiscordOptionReference
import ru.sablebot.module.moderation.model.DurationParser
import ru.sablebot.module.moderation.service.IAutoModConfigService
import java.util.UUID

@Component
class AutoModCommand(
    private val autoModConfigService: IAutoModConfigService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "automod", "Configure auto-moderation settings",
        CommandCategory.MODERATION, UUID.fromString("b1000001-0000-0000-0000-000000000020")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)

        subcommand("antispam", "Configure anti-spam protection", UUID.fromString("b1000001-0000-0000-0000-000000000021")) {
            executor = AntiSpamExecutor()
        }
        subcommand("antiraid", "Configure anti-raid protection", UUID.fromString("b1000001-0000-0000-0000-000000000022")) {
            executor = AntiRaidExecutor()
        }
        subcommandGroup("wordfilter", "Manage word filter settings") {
            subcommand("toggle", "Enable or disable the word filter", UUID.fromString("b1000001-0000-0000-0000-000000000023")) {
                executor = WordFilterToggleExecutor()
            }
            subcommand("add", "Add a word filter pattern", UUID.fromString("b1000001-0000-0000-0000-000000000024")) {
                executor = WordFilterAddExecutor()
            }
            subcommand("remove", "Remove a word filter pattern", UUID.fromString("b1000001-0000-0000-0000-000000000025")) {
                executor = WordFilterRemoveExecutor()
            }
            subcommand("list", "List all word filter patterns", UUID.fromString("b1000001-0000-0000-0000-000000000026")) {
                executor = WordFilterListExecutor()
            }
        }
        subcommandGroup("linkfilter", "Manage link filter settings") {
            subcommand("toggle", "Enable or disable the link filter", UUID.fromString("b1000001-0000-0000-0000-000000000027")) {
                executor = LinkFilterToggleExecutor()
            }
            subcommand("add", "Add a domain to the link filter", UUID.fromString("b1000001-0000-0000-0000-000000000028")) {
                executor = LinkFilterAddExecutor()
            }
            subcommand("remove", "Remove a domain from the link filter", UUID.fromString("b1000001-0000-0000-0000-000000000029")) {
                executor = LinkFilterRemoveExecutor()
            }
        }
        subcommand("mentionspam", "Configure mention spam protection", UUID.fromString("b1000001-0000-0000-0000-00000000002a")) {
            executor = MentionSpamExecutor()
        }
        subcommand("status", "View all auto-moderation settings", UUID.fromString("b1000001-0000-0000-0000-00000000002b")) {
            executor = StatusExecutor()
        }
    }

    // --- Anti-Spam ---

    inner class AntiSpamExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val enable = boolean("enable", "Enable or disable anti-spam")
            val maxMessages = optionalString("max_messages", "Max messages allowed in window (default: 5)")
            val window = optionalString("window", "Time window in seconds (default: 5)")
            val action = optionalString("action", "Action to take on trigger") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Warn", "WARN")
                choices += StringDiscordOptionReference.Choice.RawChoice("Mute", "MUTE")
                choices += StringDiscordOptionReference.Choice.RawChoice("Kick", "KICK")
            }
            val muteDuration = optionalString("mute_duration", "Mute duration (e.g. 1h, 30m, 1d)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = autoModConfigService.getOrCreate(context.guild.idLong)
            config.antiSpamEnabled = args[options.enable]

            args[options.maxMessages]?.let {
                val value = it.toIntOrNull()
                if (value == null || value < 1) {
                    context.reply(ephemeral = true, "max_messages must be a positive integer.")
                    return
                }
                config.antiSpamMaxMessages = value
            }

            args[options.window]?.let {
                val value = it.toIntOrNull()
                if (value == null || value < 1) {
                    context.reply(ephemeral = true, "window must be a positive integer.")
                    return
                }
                config.antiSpamWindowSeconds = value
            }

            args[options.action]?.let {
                config.antiSpamAction = try {
                    AutoModActionType.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    context.reply(ephemeral = true, "Invalid action type.")
                    return
                }
            }

            args[options.muteDuration]?.let {
                val duration = DurationParser.parse(it)
                if (duration == null) {
                    context.reply(ephemeral = true, "Invalid duration format. Use e.g. 1h, 30m, 1d.")
                    return
                }
                config.antiSpamMuteDuration = duration.toMillis()
            }

            autoModConfigService.save(config)
            val status = if (config.antiSpamEnabled) "enabled" else "disabled"
            context.reply(ephemeral = true, "Anti-spam $status.")
        }
    }

    // --- Anti-Raid ---

    inner class AntiRaidExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val enable = boolean("enable", "Enable or disable anti-raid")
            val joinThreshold = optionalString("join_threshold", "Number of joins to trigger (default: 10)")
            val window = optionalString("window", "Time window in seconds (default: 10)")
            val minAccountAge = optionalString("min_account_age", "Minimum account age in days (default: 7)")
            val action = optionalString("action", "Action to take on trigger") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Kick", "KICK")
                choices += StringDiscordOptionReference.Choice.RawChoice("Ban", "BAN")
            }
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = autoModConfigService.getOrCreate(context.guild.idLong)
            config.antiRaidEnabled = args[options.enable]

            args[options.joinThreshold]?.let {
                val value = it.toIntOrNull()
                if (value == null || value < 1) {
                    context.reply(ephemeral = true, "join_threshold must be a positive integer.")
                    return
                }
                config.antiRaidJoinThreshold = value
            }

            args[options.window]?.let {
                val value = it.toIntOrNull()
                if (value == null || value < 1) {
                    context.reply(ephemeral = true, "window must be a positive integer.")
                    return
                }
                config.antiRaidWindowSeconds = value
            }

            args[options.minAccountAge]?.let {
                val value = it.toIntOrNull()
                if (value == null || value < 0) {
                    context.reply(ephemeral = true, "min_account_age must be a non-negative integer.")
                    return
                }
                config.antiRaidMinAccountAgeDays = value
            }

            args[options.action]?.let {
                config.antiRaidAction = try {
                    AutoModActionType.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    context.reply(ephemeral = true, "Invalid action type.")
                    return
                }
            }

            autoModConfigService.save(config)
            val status = if (config.antiRaidEnabled) "enabled" else "disabled"
            context.reply(ephemeral = true, "Anti-raid $status.")
        }
    }

    // --- Word Filter ---

    inner class WordFilterToggleExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val enable = boolean("enable", "Enable or disable the word filter")
            val action = optionalString("action", "Action to take on trigger") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Warn", "WARN")
                choices += StringDiscordOptionReference.Choice.RawChoice("Mute", "MUTE")
                choices += StringDiscordOptionReference.Choice.RawChoice("Delete", "DELETE")
            }
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = autoModConfigService.getOrCreate(context.guild.idLong)
            config.wordFilterEnabled = args[options.enable]

            args[options.action]?.let {
                config.wordFilterAction = try {
                    AutoModActionType.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    context.reply(ephemeral = true, "Invalid action type.")
                    return
                }
            }

            autoModConfigService.save(config)
            val status = if (config.wordFilterEnabled) "enabled" else "disabled"
            context.reply(ephemeral = true, "Word filter $status.")
        }
    }

    inner class WordFilterAddExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val pattern = string("pattern", "Regex pattern to filter")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val pattern = args[options.pattern]

            try {
                Regex(pattern)
            } catch (_: Exception) {
                context.reply(ephemeral = true, "Invalid regex pattern.")
                return
            }

            val config = autoModConfigService.getOrCreate(context.guild.idLong)

            if (config.wordFilterPatterns.contains(pattern)) {
                context.reply(ephemeral = true, "Pattern `$pattern` is already in the word filter.")
                return
            }

            config.wordFilterPatterns.add(pattern)
            autoModConfigService.save(config)
            context.reply(ephemeral = true, "Pattern `$pattern` added to the word filter.")
        }
    }

    inner class WordFilterRemoveExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val pattern = string("pattern", "Regex pattern to remove")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val pattern = args[options.pattern]
            val config = autoModConfigService.getOrCreate(context.guild.idLong)
            val removed = config.wordFilterPatterns.remove(pattern)

            if (removed) {
                autoModConfigService.save(config)
                context.reply(ephemeral = true, "Pattern `$pattern` removed from the word filter.")
            } else {
                context.reply(ephemeral = true, "Pattern `$pattern` not found in the word filter.")
            }
        }
    }

    inner class WordFilterListExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = autoModConfigService.getOrCreate(context.guild.idLong)
            val patterns = config.wordFilterPatterns

            if (patterns.isEmpty()) {
                context.reply(ephemeral = true, "No word filter patterns configured.")
                return
            }

            context.reply(ephemeral = true) {
                embed {
                    title = "Word Filter Patterns"
                    description = patterns.joinToString("\n") { "`$it`" }
                    color = 0x2F3136
                }
            }
        }
    }

    // --- Link Filter ---

    inner class LinkFilterToggleExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val enable = boolean("enable", "Enable or disable the link filter")
            val mode = optionalString("mode", "Filter mode") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Whitelist", "WHITELIST")
                choices += StringDiscordOptionReference.Choice.RawChoice("Blacklist", "BLACKLIST")
            }
            val action = optionalString("action", "Action to take on trigger") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Warn", "WARN")
                choices += StringDiscordOptionReference.Choice.RawChoice("Mute", "MUTE")
                choices += StringDiscordOptionReference.Choice.RawChoice("Delete", "DELETE")
            }
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = autoModConfigService.getOrCreate(context.guild.idLong)
            config.linkFilterEnabled = args[options.enable]

            args[options.mode]?.let {
                config.linkFilterMode = try {
                    LinkFilterMode.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    context.reply(ephemeral = true, "Invalid filter mode.")
                    return
                }
            }

            args[options.action]?.let {
                config.linkFilterAction = try {
                    AutoModActionType.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    context.reply(ephemeral = true, "Invalid action type.")
                    return
                }
            }

            autoModConfigService.save(config)
            val status = if (config.linkFilterEnabled) "enabled" else "disabled"
            context.reply(ephemeral = true, "Link filter $status.")
        }
    }

    inner class LinkFilterAddExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val domain = string("domain", "Domain to add to the filter")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val domain = args[options.domain].lowercase()
            val config = autoModConfigService.getOrCreate(context.guild.idLong)

            if (config.linkFilterDomains.contains(domain)) {
                context.reply(ephemeral = true, "Domain `$domain` is already in the link filter.")
                return
            }

            config.linkFilterDomains.add(domain)
            autoModConfigService.save(config)
            context.reply(ephemeral = true, "Domain `$domain` added to the link filter.")
        }
    }

    inner class LinkFilterRemoveExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val domain = string("domain", "Domain to remove from the filter")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val domain = args[options.domain].lowercase()
            val config = autoModConfigService.getOrCreate(context.guild.idLong)
            val removed = config.linkFilterDomains.remove(domain)

            if (removed) {
                autoModConfigService.save(config)
                context.reply(ephemeral = true, "Domain `$domain` removed from the link filter.")
            } else {
                context.reply(ephemeral = true, "Domain `$domain` not found in the link filter.")
            }
        }
    }

    // --- Mention Spam ---

    inner class MentionSpamExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val enable = boolean("enable", "Enable or disable mention spam protection")
            val threshold = optionalString("threshold", "Max mentions per message (default: 5)")
            val action = optionalString("action", "Action to take on trigger") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Warn", "WARN")
                choices += StringDiscordOptionReference.Choice.RawChoice("Mute", "MUTE")
            }
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = autoModConfigService.getOrCreate(context.guild.idLong)
            config.mentionSpamEnabled = args[options.enable]

            args[options.threshold]?.let {
                val value = it.toIntOrNull()
                if (value == null || value < 1) {
                    context.reply(ephemeral = true, "threshold must be a positive integer.")
                    return
                }
                config.mentionSpamThreshold = value
            }

            args[options.action]?.let {
                config.mentionSpamAction = try {
                    AutoModActionType.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    context.reply(ephemeral = true, "Invalid action type.")
                    return
                }
            }

            autoModConfigService.save(config)
            val status = if (config.mentionSpamEnabled) "enabled" else "disabled"
            context.reply(ephemeral = true, "Mention spam protection $status.")
        }
    }

    // --- Status ---

    inner class StatusExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val config = autoModConfigService.getOrCreate(context.guild.idLong)

            context.reply(ephemeral = true) {
                embed {
                    title = "Auto-Moderation Status"
                    description = buildString {
                        appendLine("**Anti-Spam**")
                        appendLine("Enabled: ${if (config.antiSpamEnabled) "Yes" else "No"}")
                        appendLine("Max Messages: ${config.antiSpamMaxMessages}")
                        appendLine("Window: ${config.antiSpamWindowSeconds}s")
                        appendLine("Action: ${config.antiSpamAction}")
                        config.antiSpamMuteDuration?.let {
                            appendLine("Mute Duration: ${DurationParser.format(it)}")
                        }
                        appendLine()

                        appendLine("**Anti-Raid**")
                        appendLine("Enabled: ${if (config.antiRaidEnabled) "Yes" else "No"}")
                        appendLine("Join Threshold: ${config.antiRaidJoinThreshold}")
                        appendLine("Window: ${config.antiRaidWindowSeconds}s")
                        appendLine("Min Account Age: ${config.antiRaidMinAccountAgeDays} days")
                        appendLine("Action: ${config.antiRaidAction}")
                        appendLine()

                        appendLine("**Word Filter**")
                        appendLine("Enabled: ${if (config.wordFilterEnabled) "Yes" else "No"}")
                        appendLine("Patterns: ${config.wordFilterPatterns.size}")
                        appendLine("Action: ${config.wordFilterAction}")
                        appendLine()

                        appendLine("**Link Filter**")
                        appendLine("Enabled: ${if (config.linkFilterEnabled) "Yes" else "No"}")
                        appendLine("Mode: ${config.linkFilterMode}")
                        appendLine("Domains: ${config.linkFilterDomains.size}")
                        appendLine("Action: ${config.linkFilterAction}")
                        appendLine()

                        appendLine("**Mention Spam**")
                        appendLine("Enabled: ${if (config.mentionSpamEnabled) "Yes" else "No"}")
                        appendLine("Threshold: ${config.mentionSpamThreshold}")
                        appendLine("Action: ${config.mentionSpamAction}")
                    }
                    color = 0x2F3136
                }
            }
        }
    }
}
