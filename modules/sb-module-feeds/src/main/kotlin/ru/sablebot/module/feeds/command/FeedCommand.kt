package ru.sablebot.module.feeds.command

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.persistence.entity.FeedType
import ru.sablebot.common.persistence.entity.SocialFeed
import ru.sablebot.common.persistence.repository.SocialFeedRepository
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.common.worker.message.model.commands.options.StringDiscordOptionReference
import ru.sablebot.module.feeds.config.FeedProperties
import java.util.*

@Component
class FeedCommand(
    private val socialFeedRepository: SocialFeedRepository,
    private val feedProperties: FeedProperties
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "feed", "Manage social media feed notifications",
        CommandCategory.ADMIN, UUID.fromString("c1000001-0000-0000-0000-000000000001")
    ) {
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)

        subcommand("add", "Add a new social media feed", UUID.fromString("c1000001-0000-0000-0000-000000000002")) {
            executor = AddFeedExecutor()
        }
        subcommand("list", "List all configured feeds", UUID.fromString("c1000001-0000-0000-0000-000000000003")) {
            executor = ListFeedExecutor()
        }
        subcommand("remove", "Remove a feed by ID", UUID.fromString("c1000001-0000-0000-0000-000000000004")) {
            executor = RemoveFeedExecutor()
        }
        subcommand("toggle", "Enable or disable a feed", UUID.fromString("c1000001-0000-0000-0000-000000000005")) {
            executor = ToggleFeedExecutor()
        }
    }

    // --- Add Feed ---

    inner class AddFeedExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val feedType = string("type", "Type of feed (reddit/twitch/youtube)") {
                choices += StringDiscordOptionReference.Choice.RawChoice("Reddit", "REDDIT")
                choices += StringDiscordOptionReference.Choice.RawChoice("Twitch", "TWITCH")
                choices += StringDiscordOptionReference.Choice.RawChoice("YouTube", "YOUTUBE")
            }
            val target = string("target", "Subreddit name, Twitch username, or YouTube channel ID")
            val channel = channel("channel", "Discord channel for notifications")
            val interval = optionalString("interval", "Check interval in minutes (default: varies by platform)")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guildId = context.guild.idLong

                // Check feed limit
                val currentCount = socialFeedRepository.countByGuildId(guildId)
                if (currentCount >= feedProperties.limits.maxFeedsPerGuild) {
                    throw DiscordException("Feed limit reached (${feedProperties.limits.maxFeedsPerGuild} max). Remove a feed before adding a new one.")
                }

                val feedType = try {
                    FeedType.valueOf(args[options.feedType])
                } catch (_: IllegalArgumentException) {
                    throw DiscordException("Invalid feed type.")
                }

                // Validate platform is enabled
                when (feedType) {
                    FeedType.REDDIT -> {
                        if (!feedProperties.reddit.enabled) {
                            throw DiscordException("Reddit feeds are currently disabled.")
                        }
                    }
                    FeedType.TWITCH -> {
                        if (!feedProperties.twitch.enabled) {
                            throw DiscordException("Twitch feeds are currently disabled.")
                        }
                    }
                    FeedType.YOUTUBE -> {
                        if (!feedProperties.youtube.enabled) {
                            throw DiscordException("YouTube feeds are currently disabled.")
                        }
                    }
                }

                val targetIdentifier = args[options.target]
                val selectedChannel = args[options.channel]

                // Validate channel is a text channel
                if (selectedChannel !is TextChannel) {
                    throw DiscordException("The channel must be a text channel.")
                }

                val targetChannelId = selectedChannel.idLong

                // Determine default interval based on feed type
                val defaultInterval = when (feedType) {
                    FeedType.REDDIT -> feedProperties.polling.redditIntervalSeconds / 60
                    FeedType.TWITCH -> feedProperties.polling.twitchIntervalSeconds / 60
                    FeedType.YOUTUBE -> feedProperties.polling.youtubeIntervalSeconds / 60
                }

                val interval = args[options.interval]?.let { intervalStr ->
                    val value = intervalStr.toIntOrNull()
                    if (value == null || value < 1) {
                        throw DiscordException("Interval must be a positive integer (minutes).")
                    }
                    value
                } ?: defaultInterval

                val feed = SocialFeed(
                    feedType = feedType,
                    targetIdentifier = targetIdentifier,
                    targetChannelId = targetChannelId,
                    checkIntervalMinutes = interval,
                    enabled = true
                ).apply {
                    this.guildId = guildId
                }

                socialFeedRepository.save(feed)

                context.reply(ephemeral = true, "✅ Feed added successfully!\n" +
                        "**Type:** $feedType\n" +
                        "**Target:** $targetIdentifier\n" +
                        "**Channel:** ${selectedChannel.asMention}\n" +
                        "**Interval:** $interval minutes\n" +
                        "**Feed ID:** ${feed.id}")

            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }

    // --- List Feeds ---

    inner class ListFeedExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guildId = context.guild.idLong
                val feeds = socialFeedRepository.findAllByGuildId(guildId)

                if (feeds.isEmpty()) {
                    context.reply(ephemeral = true, "No feeds configured for this server.")
                    return
                }

                context.reply(ephemeral = true) {
                    embed {
                        title = "Social Media Feeds (${feeds.size}/${feedProperties.limits.maxFeedsPerGuild})"
                        description = feeds.joinToString("\n\n") { feed ->
                            val channelMention = context.guild.getTextChannelById(feed.targetChannelId)?.asMention ?: "`#deleted-channel`"
                            val status = if (feed.enabled) "✅ Enabled" else "❌ Disabled"
                            buildString {
                                appendLine("**ID:** `${feed.id}`")
                                appendLine("**Type:** ${feed.feedType}")
                                appendLine("**Target:** `${feed.targetIdentifier}`")
                                appendLine("**Channel:** $channelMention")
                                appendLine("**Interval:** ${feed.checkIntervalMinutes} minutes")
                                appendLine("**Status:** $status")
                            }
                        }
                        color = 0x2F3136
                    }
                }
            } catch (e: Exception) {
                context.reply(ephemeral = true, "An error occurred while listing feeds.")
            }
        }
    }

    // --- Remove Feed ---

    inner class RemoveFeedExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val feedId = string("feed_id", "The ID of the feed to remove")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val feedIdStr = args[options.feedId]
                val feedId = feedIdStr.toLongOrNull()
                    ?: throw DiscordException("Feed ID must be a valid number.")

                val feed = socialFeedRepository.findById(feedId).orElse(null)
                    ?: throw DiscordException("Feed with ID `$feedId` not found.")

                // Verify feed belongs to this guild
                if (feed.guildId != context.guild.idLong) {
                    throw DiscordException("Feed with ID `$feedId` not found in this server.")
                }

                socialFeedRepository.delete(feed)

                context.reply(ephemeral = true, "✅ Feed removed successfully!\n" +
                        "**Type:** ${feed.feedType}\n" +
                        "**Target:** `${feed.targetIdentifier}`")

            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }

    // --- Toggle Feed ---

    inner class ToggleFeedExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions() {
            val feedId = string("feed_id", "The ID of the feed to toggle")
            val enable = boolean("enable", "Enable or disable the feed")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val feedIdStr = args[options.feedId]
                val feedId = feedIdStr.toLongOrNull()
                    ?: throw DiscordException("Feed ID must be a valid number.")

                val feed = socialFeedRepository.findById(feedId).orElse(null)
                    ?: throw DiscordException("Feed with ID `$feedId` not found.")

                // Verify feed belongs to this guild
                if (feed.guildId != context.guild.idLong) {
                    throw DiscordException("Feed with ID `$feedId` not found in this server.")
                }

                val enableFlag = args[options.enable]
                feed.enabled = enableFlag

                socialFeedRepository.save(feed)

                val status = if (enableFlag) "enabled" else "disabled"
                context.reply(ephemeral = true, "✅ Feed $status!\n" +
                        "**Type:** ${feed.feedType}\n" +
                        "**Target:** `${feed.targetIdentifier}`")

            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }
}
