package ru.sablebot.worker.commands

import dev.minn.jda.ktx.interactions.components.SelectOption
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.InteractivityManager
import ru.sablebot.common.worker.message.model.commands.options.ApplicationCommandOptions
import ru.sablebot.common.worker.message.model.modals.options.modalString
import ru.sablebot.common.worker.message.model.styled
import java.util.*

@Component
class TestCommand(
    private val interactivityManager: InteractivityManager
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "test", "Command for testing something.",
        CommandCategory.GENERAL, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567802")
    ) {
        executor = TestExecutor()
    }

    inner class TestExecutor : SlashCommandExecutor() {
        override val options = Options()

        inner class Options : ApplicationCommandOptions()

        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
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
}