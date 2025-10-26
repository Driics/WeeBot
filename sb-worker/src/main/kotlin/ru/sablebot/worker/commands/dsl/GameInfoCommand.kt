package ru.sablebot.worker.commands.dsl

import dev.minn.jda.ktx.interactions.components.SelectOption
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.InteractivityManager
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.common.worker.modules.game.PriceProvider
import ru.sablebot.common.worker.modules.game.SteamService
import java.awt.Color
import java.text.NumberFormat
import java.util.*

@Component
class GameInfoCommand(
    private val steamService: SteamService,
    private val priceProviders: List<PriceProvider>,
    private val interactivityManager: InteractivityManager
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "game",
        "Get information about games from Steam and other platforms",
        CommandCategory.GENERAL,
        UUID.fromString("b1c2d3e4-f5a6-7890-bcde-1234567890ab")
    ) {
        subcommand(
            "search",
            "Search for a game",
            UUID.fromString("b1c2d3e4-f5a6-7890-bcde-1234567890ac")
        ) {
            executor = SearchGameExecutor()
        }

        subcommand(
            "info",
            "Get detailed information about a Steam game",
            UUID.fromString("b1c2d3e4-f5a6-7890-bcde-1234567890ad")
        ) {
            executor = GameInfoExecutor()
        }
    }

    inner class SearchGameExecutor : SlashCommandExecutor() {
        inner class Options : ApplicationCommandOptions() {
            val gameName = string("name", "Game name to search")
        }

        override val options = Options()

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val gameName = args[options.gameName]

            context.reply(true) {
                embed {
                    title = "🔍 Searching for games..."
                    description = "Searching for: **$gameName**"
                    color = Color.CYAN.rgb
                }
            }

            val searchResults = steamService.searchGame(gameName)

            if (searchResults.isEmpty()) {
                context.event.hook.editOriginal("").setEmbeds().queue { _ ->
                    context.event.hook.editOriginal("").setEmbeds(
                        net.dv8tion.jda.api.EmbedBuilder().apply {
                            setTitle("❌ No games found")
                            setDescription("No games found matching: **$gameName**")
                            setColor(Color.RED.rgb)
                        }.build()
                    ).queue()
                }
                return
            }

            context.event.hook.editOriginal("").setEmbeds().setActionRow(
                interactivityManager.stringSelectMenuForUser(
                    context.user,
                    true,
                    {
                        placeholder = "Select a game to view details..."
                        setMinValues(1)
                        setMaxValues(1)

                        addOptions(
                            searchResults.take(25).map { result ->
                                SelectOption(
                                    label = result.gameName.take(100),
                                    value = result.gameId,
                                    description = "Steam App ID: ${result.gameId}"
                                )
                            }
                        )
                    }
                ) { selectContext, selectedValues ->
                    val selectedAppId = selectedValues.firstOrNull() ?: return@stringSelectMenuForUser

                    selectContext.reply(true) {
                        embed {
                            title = "⏳ Loading game information..."
                            description = "Fetching details from Steam..."
                            color = Color.CYAN.rgb
                        }
                    }

                    showGameInfo(selectContext.event.hook, selectedAppId)
                }
            ).setEmbeds(
                net.dv8tion.jda.api.EmbedBuilder().apply {
                    setTitle("🎮 Search Results")
                    setDescription("Found ${searchResults.size} games matching: **$gameName**\n\nSelect a game from the dropdown below:")
                    setColor(Color.GREEN.rgb)
                }.build()
            ).queue()
        }
    }

    inner class GameInfoExecutor : SlashCommandExecutor() {
        inner class Options : ApplicationCommandOptions() {
            val appId = string("appid", "Steam App ID")
        }

        override val options = Options()

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val appId = args[options.appId]

            context.reply(true) {
                embed {
                    title = "⏳ Loading game information..."
                    description = "Fetching details from Steam and other platforms..."
                    color = Color.CYAN.rgb
                }
            }

            showGameInfo(context.event.hook, appId)
        }
    }

    private suspend fun showGameInfo(hook: net.dv8tion.jda.api.interactions.InteractionHook, appId: String) {
        val gameDetails = steamService.getGameDetails(appId)

        if (gameDetails == null) {
            hook.editOriginal("").setEmbeds(
                net.dv8tion.jda.api.EmbedBuilder().apply {
                    setTitle("❌ Game not found")
                    setDescription("Could not find game with Steam App ID: **$appId**")
                    setColor(Color.RED.rgb)
                }.build()
            ).queue()
            return
        }

        // Fetch prices from all providers
        val prices = coroutineScope {
            priceProviders.map { provider ->
                async {
                    try {
                        provider.getPrice(appId)
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val embed = net.dv8tion.jda.api.EmbedBuilder().apply {
            setTitle("🎮 ${gameDetails.name}")
            setUrl(gameDetails.storeUrl)

            if (gameDetails.headerImage != null) {
                setImage(gameDetails.headerImage)
            }

            if (gameDetails.shortDescription != null) {
                setDescription(gameDetails.shortDescription?.take(500))
            }

            if (gameDetails.developers.isNotEmpty()) {
                addField("👨‍💻 Developers", gameDetails.developers.joinToString(", "), true)
            }

            if (gameDetails.publishers.isNotEmpty()) {
                addField("📢 Publishers", gameDetails.publishers.joinToString(", "), true)
            }

            if (gameDetails.releaseDate != null) {
                addField("📅 Release Date", gameDetails.releaseDate ?: "-", true)
            }

            if (gameDetails.genres.isNotEmpty()) {
                addField("🎯 Genres", gameDetails.genres.joinToString(", "), false)
            }

            // Price comparison section
            if (prices.isNotEmpty()) {
                val priceText = prices.joinToString("\n\n") { priceInfo ->
                    buildString {
                        append("**${priceInfo.providerName}**: ")

                        when {
                            priceInfo.isFree -> append("Free")
                            priceInfo.isOnSale -> {
                                val formatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
                                    currency = Currency.getInstance(priceInfo.currency)
                                }
                                append("~~${formatter.format(priceInfo.originalPrice)}~~ ")
                                append("**${formatter.format(priceInfo.finalPrice)}** ")
                                append("(-${priceInfo.discountPercentage}%)")
                            }

                            else -> {
                                val formatter = NumberFormat.getCurrencyInstance(Locale.US).apply {
                                    currency = Currency.getInstance(priceInfo.currency)
                                }
                                append(formatter.format(priceInfo.finalPrice))
                            }
                        }
                    }
                }
                addField("💰 Prices", priceText, false)
            } else {
                addField("💰 Price", "Price information unavailable", false)
            }

            setColor(if (gameDetails.isFree) Color.GREEN.rgb else Color.CYAN.rgb)

            setFooter("Steam App ID: $appId")
        }.build()

        hook.editOriginal("").setEmbeds(embed).queue()
    }
}