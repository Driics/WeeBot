package ru.sablebot.worker.commands.dsl.admin

import dev.minn.jda.ktx.interactions.components.SelectOption
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import org.springframework.stereotype.Component
import ru.sablebot.common.model.AuditActionType
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.service.AuditConfigService
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.InteractivityManager
import ru.sablebot.common.worker.message.model.styled
import java.awt.Color
import java.util.*

@Component
class AuditCommand(
    private val interactivityManager: InteractivityManager,
    private val auditConfigService: AuditConfigService
) : SlashCommandDeclarationWrapper {
    override fun command() = slashCommand(
        "audit",
        "Control of server audit system",
        CommandCategory.ADMIN,
        UUID.fromString("78b4ca48-5733-463a-ba07-2cc9d514804f")
    ) {
        defaultMemberPermissions =
            DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER, Permission.ADMINISTRATOR)

        subcommand(
            "enable",
            "Включить систему аудита на сервере",
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891")
        ) {
            executor = EnableAuditExecutor()
        }

        /*subcommand(
            "disable",
            "Отключить систему аудита на сервере",
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567892")
        ) {
            executor = DisableAuditExecutor()
        }*/

        subcommand(
            "status",
            "Показать текущий статус системы аудита",
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567893")
        ) {
            executor = StatusAuditExecutor()
        }

        subcommand(
            "setactions",
            "Настроить отслеживаемые действия для аудита",
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567895")
        ) {
            executor = SetActionsAuditExecutor()
        }

        /*subcommand(
            "setchannel",
            "Установить канал для отправки сообщений аудита",
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567894")
        ) {
            executor = SetChannelAuditExecutor()
        }*/
    }

    inner class EnableAuditExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val guild = context.event.guild ?: run {
                context.reply(true) {
                    styled(" Эта команда доступна только на серверах!", "❌")
                }
                return
            }

            val config = auditConfigService.getOrCreate(guild.idLong)

            if (config.enabled) {
                context.reply(true) {
                    styled("ℹ️ Система аудита уже включена на этом сервере.", "Информация")
                    actionRow(
                        interactivityManager.buttonForUser(
                            context.user,
                            true,
                            ButtonStyle.DANGER,
                            "Disable audit service",
                            {}
                        ) { callbackCtx ->
                            config.enabled = false
                            auditConfigService.save(config)

                            callbackCtx.reply(true) {
                                styled("Audit service disabled", "❌")
                            }
                        }
                    )
                }
                return
            }

            config.enabled = true
            auditConfigService.save(config)

            context.reply(true) {
                styled(
                    "Система аудита успешно включена!\n\nИспользуйте `/audit setchannel` для настройки канала логов.",
                    "✅"
                )
            }
        }
    }

    inner class StatusAuditExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val guild = context.event.guild ?: run {
                context.reply(true) {
                    embed {
                        title = "❌ Error"
                        description = "This command is only available on servers!"
                        color = Color.RED.rgb
                    }
                }
                return
            }

            val config = auditConfigService.getByGuildId(guild.idLong) ?: run {
                context.reply(true) {
                    styled("Something went wrong. Config not found", "😟")
                }
                return
            }

            val statusEmoji = if (config.enabled) "🟢" else "🔴"
            val statusText = if (config.enabled) "Enabled" else "Disabled"

            val forwardStatus = if (config.forwardEnabled) {
                val channel = guild.getTextChannelById(config.forwardChannelId)
                if (channel != null) {
                    "🟢 Enabled → ${channel.asMention}"
                } else {
                    "⚠️ Enabled, but the channel was not set"
                }
            } else {
                "🔴 Disabled"
            }

            val actionsText = if (config.forwardActions.isNotEmpty()) {
                config.forwardActions.map(::formatActionTypeName).joinToString(", ") { "`${it}`" }
            } else {
                "Not set"
            }

            context.reply(true) {
                embed {
                    title = "⚙️ Audit Configuration"
                    description = "The current status of the audit system on the server"

                    field {
                        name = "System status"
                        value = "$statusEmoji **$statusText**"
                        inline = false
                    }

                    field {
                        name = "Forwarding messages"
                        value = forwardStatus
                        inline = false
                    }

                    field {
                        name = "Tracked actions"
                        value = actionsText
                        inline = false
                    }

                    color = if (config.enabled) Color.GREEN.rgb else Color.GRAY.rgb

                    footer {
                        name = "Use the /audit commands to configure"
                    }
                }
            }
        }
    }

    inner class SetActionsAuditExecutor : SlashCommandExecutor() {
        override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
            val guild = context.event.guild ?: run {
                context.reply(true) {
                    embed {
                        title = "❌ Error"
                        description = "This command is only available on servers!"
                        color = Color.RED.rgb
                    }
                }
                return
            }

            val config = auditConfigService.getByGuildId(guild.idLong) ?: run {
                context.reply(true) {
                    styled("Something went wrong. Config not found", "😟")
                }
                return
            }

            if (!config.enabled) {
                context.reply(true) {
                    embed {
                        title = "⚠️ Warning"
                        description = "The audit system is disabled.\nUse `/audit enable` to enable it."
                        color = Color.ORANGE.rgb
                    }
                }
                return
            }

            val currentActions = config.forwardActions

            context.reply(true) {
                embed {
                    title = "🎯 Configuring Tracked Actions"
                    description = "Select the actions that will be monitored by the audit system.\n\n" +
                            "**Current actions:** ${
                                if (currentActions.isEmpty()) "Not set" else currentActions.joinToString(
                                    ", "
                                ) { it.name }
                            }"
                    color = Color.CYAN.rgb
                }

                actionRow(
                    interactivityManager.stringSelectMenuForUser(
                        context.user,
                        true,
                        {
                            placeholder = "Select the actions to track..."
                            setMinValues(0)
                            setMaxValues(AuditActionType.entries.size)

                            // Add options for each audit action type
                            addOptions(
                                AuditActionType.entries.map { actionType ->
                                    SelectOption(
                                        label = formatActionTypeName(actionType),
                                        value = actionType.name,
                                        description = getActionTypeDescription(actionType),
                                        default = currentActions.contains(actionType)
                                    )
                                }
                            )
                        }
                    ) { selectContext, selectedValues ->
                        val selectedActions = selectedValues.mapNotNull { value ->
                            try {
                                AuditActionType.valueOf(value)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        }

                        config.forwardActions = selectedActions
                        auditConfigService.save(config)

                        selectContext.reply(true) {
                            embed {
                                title = "✅ Actions updated"
                                description = if (selectedActions.isEmpty()) {
                                    "Tracking of actions is disabled."
                                } else {
                                    "**Tracked actions:**\n" +
                                            selectedActions.joinToString(", ") { "`${formatActionTypeName(it)}`" }
                                }
                                color = Color.GREEN.rgb
                            }
                        }
                    }
                )
            }
        }

        private fun getActionTypeDescription(type: AuditActionType): String {
            return when (type) {
                AuditActionType.BOT_ADD -> "Добавление бота на сервер"
                AuditActionType.BOT_LEAVE -> "Удаление бота с сервера"
                AuditActionType.MEMBER_JOIN -> "Новый участник"
                AuditActionType.MEMBER_NAME_CHANGE -> "Изменение никнейма"
                AuditActionType.MEMBER_LEAVE -> "Участник ушёл"
                AuditActionType.MEMBER_WARN -> "Выдача предупреждения"
                AuditActionType.MEMBER_BAN -> "Блокировка участника"
                AuditActionType.MEMBER_UNBAN -> "Снятие блокировки"
                AuditActionType.MEMBER_KICK -> "Исключение участника"
                AuditActionType.MEMBER_MUTE -> "Отключение чата"
                AuditActionType.MEMBER_UNMUTE -> "Включение чата"
                AuditActionType.MESSAGE_DELETE -> "Удалённое сообщение"
                AuditActionType.MESSAGES_CLEAR -> "Массовое удаление"
                AuditActionType.MESSAGE_EDIT -> "Изменённое сообщение"
                AuditActionType.VOICE_JOIN -> "Присоединился к голосовому"
                AuditActionType.VOICE_MOVE -> "Перемещён между каналами"
                AuditActionType.VOICE_LEAVE -> "Покинул голосовой"
            }
        }
    }

    private fun formatActionTypeName(type: AuditActionType): String {
        return when (type) {
            AuditActionType.BOT_ADD -> "Бот добавлен"
            AuditActionType.BOT_LEAVE -> "Бот покинул сервер"
            AuditActionType.MEMBER_JOIN -> "Участник присоединился"
            AuditActionType.MEMBER_NAME_CHANGE -> "Смена имени участника"
            AuditActionType.MEMBER_LEAVE -> "Участник покинул сервер"
            AuditActionType.MEMBER_WARN -> "Предупреждение участника"
            AuditActionType.MEMBER_BAN -> "Бан участника"
            AuditActionType.MEMBER_UNBAN -> "Разбан участника"
            AuditActionType.MEMBER_KICK -> "Кик участника"
            AuditActionType.MEMBER_MUTE -> "Мут участника"
            AuditActionType.MEMBER_UNMUTE -> "Размут участника"
            AuditActionType.MESSAGE_DELETE -> "Удаление сообщения"
            AuditActionType.MESSAGES_CLEAR -> "Очистка сообщений"
            AuditActionType.MESSAGE_EDIT -> "Редактирование сообщения"
            AuditActionType.VOICE_JOIN -> "Вход в голосовой канал"
            AuditActionType.VOICE_MOVE -> "Перемещение в голосовом канале"
            AuditActionType.VOICE_LEAVE -> "Выход из голосового канала"
        }
    }
}