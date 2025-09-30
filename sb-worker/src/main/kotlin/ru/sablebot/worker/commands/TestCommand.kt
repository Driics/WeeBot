package ru.sablebot.worker.commands

import dev.minn.jda.ktx.interactions.components.SelectOption
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import ru.sablebot.common.worker.command.model.AbstractCommand
import ru.sablebot.common.worker.command.model.DiscordCommand
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.message.model.modals.options.modalString
import ru.sablebot.common.worker.message.model.styled

@DiscordCommand(
    key = "test",
    description = "Command for testing something.",
    priority = 0
)
class TestCommand : AbstractCommand() {
    override fun execute(
        event: SlashCommandInteractionEvent,
        context: ApplicationCommandContext,
        args: SlashCommandArguments
    ) {
        context.reply(true) {
            actionRow(
                interactivityManager.entitySelectMenu(true, {
                    setEntityTypes(EntitySelectMenu.SelectTarget.USER)
                }, { context, mentionables ->
                    context.reply(true) {
                        val sb = StringBuilder()
                        mentionables.forEach {
                            sb.append(it.asMention).append("\n")
                        }
                        styled(sb.toString(), "")
                    }
                }),
            )
            actionRow(
                interactivityManager.stringSelectMenu(true, {
                    addOptions(
                        SelectOption("Arcane Mage", "mage-arcane"),
                        SelectOption("Fire Mage", "mage-fire"),
                        SelectOption("Frost Mage", "mage-frost"),
                    )
                    setDefaultValues("mage-arcane")
                }, { context, mentionables ->
                    context.reply(true) {
                        val sb = StringBuilder()
                        mentionables.forEach {
                            sb.append(it).append("\n")
                        }
                        styled(sb.toString(), "")
                    }
                }),
            )
            actionRow(
                interactivityManager.button(
                    context.alwaysEphemeral,
                    ButtonStyle.PRIMARY,
                    "Test"
                ) { callbackCtx ->
                    callbackCtx.reply(true) {
                        styled(callbackCtx.event.componentId, ":smile:")
                    }
                },

                interactivityManager.button(
                    context.alwaysEphemeral,
                    ButtonStyle.SECONDARY,
                    "Modal Test"
                ) { callbackCtx ->
                    val testModalString = modalString(
                        "Test text",
                        TextInputStyle.SHORT,
                        value = "Test text",
                    )

                    callbackCtx.sendModal(
                        "Modal Test",
                        listOf(ActionRow.of(testModalString.toJDA()))
                    ) { it, args ->
                        it.reply(it.alwaysEphemeral) {
                            styled(args[testModalString], "Args")
                        }
                    }
                }
            )
        }
    }
}