package ru.sablebot.worker.commands.dsl

import dev.minn.jda.ktx.interactions.components.option
import net.dv8tion.jda.api.utils.TimeFormat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.model.status.ShardDto
import ru.sablebot.common.model.status.StatusDto
import ru.sablebot.common.service.GatewayService
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.InteractivityManager
import java.awt.Color
import java.util.*

@Component
class ShardsCommand : SlashCommandDeclarationWrapper {

    @Autowired
    private lateinit var interactivityManager: InteractivityManager

    @Autowired
    private lateinit var gatewayService: GatewayService

    override fun command() = slashCommand(
        "shards",
        "View detailed shard information",
        CommandCategory.GENERAL,
        UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    ) {
        executor = ShardsCommandExecutor()
    }

    inner class ShardsCommandExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            // Defer the reply immediately to avoid timeout
            val hook = context.deferChannelMessage(true)

            try {
                val statusDto = gatewayService.getWorkerStatus()
                val allShards = statusDto.shards

                if (allShards.isEmpty()) {
                    hook.editOriginal {
                        embed {
                            title = "❌ Error"
                            description = "No shards found in worker status"
                            color = Color.RED.rgb
                        }
                    }
                    return
                }

                // Start with the first shard (shard 0)
                val currentShardId = 0
                displayShardInfo(hook, currentShardId, allShards, statusDto)
            } catch (e: Exception) {
                hook.editOriginal {
                    embed {
                        title = "❌ Error"
                        description = "Failed to retrieve worker status: ${e.message}"
                        color = Color.RED.rgb
                    }
                }
            }
        }

        private suspend fun displayShardInfo(
            hook: ru.sablebot.common.worker.message.model.InteractionHook,
            shardId: Int,
            allShards: List<ShardDto>,
            statusDto: StatusDto
        ) {
            val shardInfo = allShards.find { it.id == shardId }
            if (shardInfo == null) {
                hook.editOriginal {
                    embed {
                        title = "❌ Error"
                        description = "Shard #$shardId not found"
                        color = Color.RED.rgb
                    }
                }
                return
            }

            hook.editOriginal {
                embed {
                    title = "📊 Shard #${shardInfo.id} Information"
                    color = if (shardInfo.connected) Color.GREEN.rgb else Color.RED.rgb
                    description = "Individual shard statistics from worker status"

                    field {
                        name = "Status"
                        value = "🔌 ${if (shardInfo.connected) "CONNECTED" else "DISCONNECTED"}"
                        inline = true
                    }
                    field {
                        name = "Ping"
                        value = "⏱️ ${shardInfo.ping}ms"
                        inline = true
                    }
                    field {
                        name = "Guilds"
                        value = "🏰 ${shardInfo.guilds}"
                        inline = true
                    }
                    field {
                        name = "Users"
                        value = "👥 ${shardInfo.users}"
                        inline = true
                    }
                    field {
                        name = "Channels"
                        value = "📺 ${shardInfo.channels}"
                        inline = true
                    }
                    field {
                        name = "Total Shards"
                        value = "🔢 ${allShards.size}"
                        inline = true
                    }

                    // Add overall statistics from StatusDto
                    field {
                        name = "Overall Statistics"
                        value = """
                            🏰 **Total Guilds**: ${statusDto.guildCount}
                            👥 **Total Users**: ${statusDto.userCount}
                            📺 **Text Channels**: ${statusDto.textChannelCount}
                            🔊 **Voice Channels**: ${statusDto.voiceChannelCount}
                            ⏱️ **Uptime**: ${TimeFormat.RELATIVE.format(System.currentTimeMillis() - statusDto.uptimeDuration)}
                            🚀 **Commands Executed**: ${statusDto.executedCommands}
                        """.trimIndent()
                        inline = false
                    }

                    footer {
                        name = "Select a shard from the dropdown below"
                    }
                }

                actionRow(
                    createShardSelectMenu(allShards, shardId, statusDto)
                )
            }
        }

        private fun createShardSelectMenu(
            allShards: List<ShardDto>,
            currentShardId: Int,
            statusDto: StatusDto
        ): net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu {
            return interactivityManager.stringSelectMenu(true, {
                placeholder = "Select a shard to view"

                // Add option for each shard
                allShards.forEach { shard ->
                    option("Shard #${shard.id}", shard.id.toString())
                }

                // Set default to current shard
                setDefaultValues(currentShardId.toString())

            }, { callbackContext, selectedValues ->
                val selectedShardId = selectedValues.first().toInt()
                val selectedShardInfo = allShards.find { it.id == selectedShardId }

                if (selectedShardInfo == null) {
                    callbackContext.reply(true) {
                        embed {
                            title = "❌ Error"
                            description = "Shard #$selectedShardId not found"
                            color = Color.RED.rgb
                        }
                    }
                    return@stringSelectMenu
                }

                // Update message with new shard data
                callbackContext.editMessage {
                    embed {
                        title = "📊 Shard #${selectedShardInfo.id} Information"
                        color = if (selectedShardInfo.connected) Color.GREEN.rgb else Color.RED.rgb
                        description = "Individual shard statistics from worker status"

                        field {
                            name = "Status"
                            value = "🔌 ${if (selectedShardInfo.connected) "CONNECTED" else "DISCONNECTED"}"
                            inline = true
                        }
                        field {
                            name = "Ping"
                            value = "⏱️ ${selectedShardInfo.ping}ms"
                            inline = true
                        }
                        field {
                            name = "Guilds"
                            value = "🏰 ${selectedShardInfo.guilds}"
                            inline = true
                        }
                        field {
                            name = "Users"
                            value = "👥 ${selectedShardInfo.users}"
                            inline = true
                        }
                        field {
                            name = "Channels"
                            value = "📺 ${selectedShardInfo.channels}"
                            inline = true
                        }
                        field {
                            name = "Total Shards"
                            value = "🔢 ${allShards.size}"
                            inline = true
                        }

                        // Add overall statistics from StatusDto
                        field {
                            name = "Overall Statistics"
                            value = """
                                🏰 **Total Guilds**: ${statusDto.guildCount}
                                👥 **Total Users**: ${statusDto.userCount}
                                📺 **Text Channels**: ${statusDto.textChannelCount}
                                🔊 **Voice Channels**: ${statusDto.voiceChannelCount}
                                ⏱️ **Uptime**: ${TimeFormat.RELATIVE.format(System.currentTimeMillis() - statusDto.uptimeDuration)}
                                🚀 **Commands Executed**: ${statusDto.executedCommands}
                            """.trimIndent()
                            inline = false
                        }

                        footer {
                            name = "Select a shard from the dropdown below"
                        }
                    }

                    actionRow(
                        createShardSelectMenu(allShards, selectedShardId, statusDto)
                    )
                }
            })
        }

    }
}
