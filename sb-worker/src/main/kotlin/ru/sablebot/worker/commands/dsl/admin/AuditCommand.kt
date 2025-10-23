package ru.sablebot.worker.commands.dsl.admin

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import org.springframework.stereotype.Component
import ru.sablebot.common.model.CommandCategory
import ru.sablebot.common.service.AuditConfigService
import ru.sablebot.common.worker.command.model.SlashCommandArguments
import ru.sablebot.common.worker.command.model.context.ApplicationCommandContext
import ru.sablebot.common.worker.command.model.dsl.SlashCommandDeclarationWrapper
import ru.sablebot.common.worker.command.model.dsl.SlashCommandExecutor
import ru.sablebot.common.worker.command.model.dsl.slashCommand
import ru.sablebot.common.worker.message.model.InteractivityManager
import ru.sablebot.common.worker.message.model.styled
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
        defaultMemberPermissions = DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER)

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
                    styled("❌ Эта команда доступна только на серверах!", "Ошибка")
                }
                return
            }

            val config = auditConfigService.getByGuildId(guild.idLong) ?: run {
                context.reply(true) {
                    styled("Something went wrong. I can't found config", "😟")
                }
                return
            }

            val statusEmoji = if (config.enabled) "🟢" else "🔴"
            val statusText = if (config.enabled) "Включена" else "Отключена"

            val forwardStatus = if (config.forwardEnabled) {
                val channel = guild.getTextChannelById(config.forwardChannelId)
                if (channel != null) {
                    "🟢 Включена → ${channel.asMention}"
                } else {
                    "⚠️ Включена, но канал не найден"
                }
            } else {
                "🔴 Отключена"
            }

            val actionsText = if (config.forwardActions.isNotEmpty()) {
                config.forwardActions.joinToString(", ")
            } else {
                "Не настроены"
            }

            context.reply(true) {
                styled(
                    """
                    **Статус системы аудита**
                    
                    $statusEmoji Система аудита: **$statusText**
                    
                    **Пересылка сообщений**
                    $forwardStatus
                    
                    **Отслеживаемые действия**
                    $actionsText
                    """.trimIndent(),
                    "Конфигурация аудита"
                )
            }
        }
    }
}