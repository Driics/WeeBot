package ru.sablebot.worker.listeners

import dev.minn.jda.ktx.interactions.commands.updateCommands
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import org.springframework.beans.factory.annotation.Autowired
import ru.driics.sablebot.common.worker.command.model.Command
import ru.driics.sablebot.common.worker.command.service.CommandsHolderService
import ru.driics.sablebot.common.worker.event.DiscordEvent
import ru.driics.sablebot.common.worker.event.listeners.DiscordEventListener
import ru.driics.sablebot.common.worker.shared.service.DiscordService
import net.dv8tion.jda.api.interactions.commands.Command as CommandJDA


@DiscordEvent
class SlashCommandRegistrationListener @Autowired constructor(
    private val holderService: CommandsHolderService,
    private val discordService: DiscordService,
) : DiscordEventListener() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReady(event: ReadyEvent) {
        event.jda.guilds.forEach { guild ->
            val registeredCommands = updateCommands(guild.idLong, event.jda) { commands ->
                event.jda.updateCommands {
                    addCommands(*commands.toTypedArray())
                }.complete()
            }
            logger.info { "${registeredCommands.size} commands registered" }
        }
    }

    private fun updateCommands(
        guildId: Long,
        jda: JDA,
        action: (List<CommandData>) -> List<CommandJDA>
    ): List<CommandJDA> {
        logger.info { "Starting command update for ${if (guildId == 0L) "global scope" else "guild ID: $guildId"}" }

        val publicCommands = holderService.publicCommands.values.map(Command::commandData)

        // Log command type breakdown once instead of per command
        CommandJDA.Type.entries
            .filter { it != CommandJDA.Type.UNKNOWN }
            .forEach { type ->
                logger.info { "Type ${type.name}: ${publicCommands.count { it.type == type }} commands" }
            }

        // Fetch commands based on the guildId (0L for global)
        val existingCommands = fetchExistingCommands(guildId, jda) ?: return emptyList()

        val needsUpdate = publicCommands.size != existingCommands.size ||
                publicCommands.any { appCommand ->
                    existingCommands.none {
                        val appCommandData = SlashCommandData.fromData(appCommand.toData())

                        it.name == appCommandData.name &&
                                it.type == appCommandData.type &&
                                it.options == appCommandData.options &&
                                it.defaultPermissions == appCommand.defaultPermissions
                    }
                }

        return if (needsUpdate) {
            logger.info { "Command mismatch detected. Updating commands for ${if (guildId == 0L) "global" else "guild ID: $guildId"}" }
            // Perform update and log the results
            performCommandUpdate(guildId, jda, publicCommands)
            val updatedCommands = action(publicCommands)
            logger.info { "Successfully updated ${updatedCommands.size} commands for ${if (guildId == 0L) "global" else "guild ID: $guildId"}" }
            updatedCommands
        } else {
            logger.info { "No update needed. Commands are already up-to-date for ${if (guildId == 0L) "global" else "guild ID: $guildId"}" }
            existingCommands
        }
    }

    // Helper function to retrieve commands for either global or specific guild
    private fun fetchExistingCommands(guildId: Long, jda: JDA): List<CommandJDA>? {
        return if (guildId == 0L) {
            logger.info { "Retrieving global commands..." }
            jda.retrieveCommands().complete()
        } else {
            val guild = discordService.getGuildById(guildId) ?: run {
                logger.warn { "Guild with ID $guildId not found" }
                return null
            }
            logger.info { "Retrieving commands for guild ID: $guildId" }
            guild.retrieveCommands().complete()
        }
    }

    // Helper function to update commands for either global or specific guild
    private fun performCommandUpdate(guildId: Long, jda: JDA, publicCommands: List<CommandData>) {
        if (guildId == 0L) {
            jda.updateCommands()
                .addCommands(publicCommands)
                .queue()
            logger.info { "Global commands updated successfully." }
        } else {
            val guild = discordService.getGuildById(guildId) ?: run {
                logger.warn { "Guild with ID $guildId not found" }
                return
            }
            guild.updateCommands()
                .addCommands(publicCommands)
                .queue()
            logger.info { "Commands updated for guild ID: $guildId" }
        }
    }
}