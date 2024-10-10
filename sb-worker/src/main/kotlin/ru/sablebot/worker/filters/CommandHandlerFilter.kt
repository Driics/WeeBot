package ru.sablebot.worker.filters

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import ru.driics.sablebot.common.worker.command.service.CommandHandler
import ru.driics.sablebot.common.worker.event.intercept.Filter
import ru.driics.sablebot.common.worker.event.intercept.FilterChain
import ru.driics.sablebot.common.worker.event.intercept.MemberMessageFilter
import kotlin.math.log

@Component
@Order(Filter.HANDLE_FILTER)
class CommandHandlerFilter @Autowired constructor(
    private val handlers: List<CommandHandler>
): MemberMessageFilter() {
    private val log = LoggerFactory.getLogger(CommandHandlerFilter::class.java)

    override fun doInternal(event: SlashCommandInteractionEvent, chain: FilterChain<SlashCommandInteractionEvent>) {
        log.info("${event.name}: ${event.user.name}")

        for (handler in handlers) {
            try {
                if (handler.handleSlashCommand(event)) {
                    break
                }
            } catch (t: Throwable) {
                log.warn("Could not handle command", t)
            }
        }
        chain.doFilter(event)
    }
}