package ru.sablebot.api.dto

import kotlinx.serialization.Serializable
import org.slf4j.MDC

@Serializable
class ErrorDetailsDto(e: Exception) {
    val error: String = e.javaClass.name
    val description: String = e.message ?: ""
    val requestId: String = MDC.get("requestId")
}