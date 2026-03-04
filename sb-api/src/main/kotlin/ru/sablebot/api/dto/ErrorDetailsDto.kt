package ru.sablebot.api.dto

import org.slf4j.MDC

data class ErrorDetailsDto(
    val error: String,
    val description: String,
    val requestId: String = MDC.get("requestId") ?: "unknown"
)
