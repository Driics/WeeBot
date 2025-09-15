package ru.sablebot.common.worker.command.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

@Service
class CommandCoolDownManager {
    companion object {
        private val logger = KotlinLogging.logger {  }
    }
}