package ru.sablebot.module.audio.command

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.exception.DiscordException
import ru.sablebot.common.persistence.entity.PlaylistItem
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.module.audio.service.IPlaylistService
import ru.sablebot.module.audio.service.PlayerServiceV4
import java.util.*

@Component
class PlaylistCommand(
    private val playerService: PlayerServiceV4,
    private val playlistService: IPlaylistService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "playlist", "Manage playlists",
        CommandCategory.MUSIC, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567820")
    ) {
        subcommand(
            "save",
            "Save the current queue as a playlist",
            UUID.fromString("b1b2c3d4-e5f6-7890-abcd-ef1234567801")
        ) {
            executor = SaveExecutor()
        }
        subcommand("load", "Load a saved playlist", UUID.fromString("b1b2c3d4-e5f6-7890-abcd-ef1234567802")) {
            executor = LoadExecutor()
        }
        subcommand("delete", "Delete a saved playlist", UUID.fromString("b1b2c3d4-e5f6-7890-abcd-ef1234567803")) {
            executor = DeleteExecutor()
        }
        subcommand(
            "list",
            "List all playlists for this server",
            UUID.fromString("b1b2c3d4-e5f6-7890-abcd-ef1234567804")
        ) {
            executor = ListExecutor()
        }
        subcommand("show", "Show tracks in a playlist", UUID.fromString("b1b2c3d4-e5f6-7890-abcd-ef1234567805")) {
            executor = ShowExecutor()
        }
    }

    inner class SaveExecutor : SlashCommandExecutor() {
        override val options = SaveOptions()

        inner class SaveOptions : ApplicationCommandOptions() {
            val name = string("name", "Name for the playlist")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                val instance = AudioCommandPreconditions.requireActivePlayer(guild, playerService)
                val queue = instance.queueSnapshot()

                if (queue.isEmpty()) {
                    throw DiscordException("The queue is empty. Nothing to save.")
                }

                context.deferChannelMessage(false)

                val playlistName = args[options.name]
                val items = queue.map { track ->
                    PlaylistItem(null).apply {
                        title = track.title ?: "Unknown"
                        author = track.author ?: "Unknown"
                        identifier = track.identifier ?: ""
                        uri = track.uri ?: ""
                        length = track.lengthMs
                        stream = track.isStream
                        artworkUri = track.artworkUrl ?: ""
                    }
                }

                playlistService.create(member, guild, playlistName, items)
                context.reply(ephemeral = false, "Saved **${queue.size}** track(s) as playlist **$playlistName**.")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }

    inner class LoadExecutor : SlashCommandExecutor() {
        override val options = LoadOptions()

        inner class LoadOptions : ApplicationCommandOptions() {
            val name = string("name", "Name or ID of the playlist to load")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                AudioCommandPreconditions.requireVoiceChannel(member)

                context.deferChannelMessage(false)

                val nameOrId = args[options.name]
                val playlists = playlistService.getByGuild(guild.idLong)
                val playlist = playlists.firstOrNull { it.uuid == nameOrId }
                    ?: playlists.firstOrNull { nameOrId.toLongOrNull()?.let { id -> it.id == id } == true }

                if (playlist == null) {
                    throw DiscordException("Playlist not found. Use `/playlist list` to see available playlists.")
                }

                val channel = context.channel as TextChannel
                playlistService.loadAndPlay(playlist.id!!, guild, member, channel)
                context.reply(ephemeral = false, "Loading playlist with **${playlist.items.size}** track(s)...")
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }

    inner class DeleteExecutor : SlashCommandExecutor() {
        override val options = DeleteOptions()

        inner class DeleteOptions : ApplicationCommandOptions() {
            val name = string("name", "Name or ID of the playlist to delete")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val member = context.member
                val guild = context.guild
                val nameOrId = args[options.name]

                val playlists = playlistService.getByGuild(guild.idLong)
                val playlist = playlists.firstOrNull { it.uuid == nameOrId }
                    ?: playlists.firstOrNull { nameOrId.toLongOrNull()?.let { id -> it.id == id } == true }

                if (playlist == null) {
                    throw DiscordException("Playlist not found.")
                }

                val deleted = playlistService.delete(playlist.id!!, member.idLong)
                if (deleted) {
                    context.reply(ephemeral = false, "Deleted playlist **${playlist.uuid}**.")
                } else {
                    context.reply(ephemeral = true, "Could not delete the playlist. You may not have permission.")
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }

    inner class ListExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild
                val playlists = playlistService.getByGuild(guild.idLong)

                if (playlists.isEmpty()) {
                    context.reply(ephemeral = false, "No playlists saved for this server.")
                    return
                }

                context.reply(ephemeral = false) {
                    embed {
                        title = "Playlists"
                        description = buildString {
                            playlists.forEachIndexed { i, pl ->
                                appendLine("`${i + 1}.` **${pl.uuid}** - ${pl.items.size} track(s)")
                            }
                        }
                        color = 0x2F3136
                    }
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }
    }

    inner class ShowExecutor : SlashCommandExecutor() {
        override val options = ShowOptions()

        inner class ShowOptions : ApplicationCommandOptions() {
            val name = string("name", "Name or ID of the playlist to show")
        }

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            try {
                val guild = context.guild
                val nameOrId = args[options.name]

                val playlists = playlistService.getByGuild(guild.idLong)
                val playlist = playlists.firstOrNull { it.uuid == nameOrId }
                    ?: playlists.firstOrNull { nameOrId.toLongOrNull()?.let { id -> it.id == id } == true }

                if (playlist == null) {
                    throw DiscordException("Playlist not found.")
                }

                val items = playlist.items
                if (items.isEmpty()) {
                    context.reply(ephemeral = false, "Playlist **${playlist.uuid}** is empty.")
                    return
                }

                val display = items.take(20)
                context.reply(ephemeral = false) {
                    embed {
                        title = "Playlist: ${playlist.uuid}"
                        description = buildString {
                            display.forEachIndexed { i, item ->
                                appendLine("`${i + 1}.` ${item.title} - ${formatDuration(item.length)}")
                            }
                            if (items.size > 20) {
                                appendLine()
                                appendLine("...and ${items.size - 20} more")
                            }
                        }
                        color = 0x2F3136
                        footer {
                            name = "${items.size} total track(s)"
                        }
                    }
                }
            } catch (e: DiscordException) {
                context.reply(ephemeral = true, e.message ?: "An error occurred")
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
    }
}
