package ru.sablebot.worker.filters

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import ru.sablebot.common.worker.command.service.CommandHandler
import ru.sablebot.common.worker.event.intercept.Filter
import ru.sablebot.common.worker.event.intercept.FilterChain
import ru.sablebot.common.worker.event.intercept.MemberMessageFilter

@Component
@Order(Filter.HANDLE_FILTER)
class CommandHandlerFilter @Autowired constructor(
    private val handlers: List<CommandHandler>
): MemberMessageFilter() {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun doInternal(event: SlashCommandInteractionEvent, chain: FilterChain<SlashCommandInteractionEvent>) {
        log.info("{}: {}", event.name, event.user.name)

        var handled = false
        for (handler in handlers) {
            try {
                if (handler.handleSlashCommand(event)) {
                    handled = true
                    break
                }
            } catch (t: Throwable) {
                log.warn("Could not handle command", t)
            }
        }

        if (!handled) {
            chain.doFilter(event)
        }
    }
}