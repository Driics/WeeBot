package ru.sablebot.worker

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import ru.sablebot.common.support.ModuleMessageSource
import ru.sablebot.common.worker.configuration.WorkerConfiguration

@SpringBootApplication
@Import(WorkerConfiguration::class)
open class SablebotWorkerApplication {
    @Bean
    open fun auditMessages(): ModuleMessageSource = ModuleMessageSource("audit-sbmessages")
}

object Launcher {

    @JvmStatic
    fun main(args: Array<String>) {
        SpringApplication(SablebotWorkerApplication::class.java).run(*args)
    }
}