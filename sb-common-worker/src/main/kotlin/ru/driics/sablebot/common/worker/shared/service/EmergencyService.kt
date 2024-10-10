package ru.driics.sablebot.common.worker.shared.service

interface EmergencyService {
    fun error(message: String, throwable: Throwable? = null)
}