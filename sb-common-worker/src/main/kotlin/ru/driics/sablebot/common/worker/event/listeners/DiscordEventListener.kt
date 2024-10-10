package ru.driics.sablebot.common.worker.event.listeners

import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component

@Component
abstract class DiscordEventListener: ListenerAdapter() {
    @Autowired
    protected lateinit var taskExecutor: TaskExecutor
    @Autowired
    protected lateinit var applicatonContext: ApplicationContext
}