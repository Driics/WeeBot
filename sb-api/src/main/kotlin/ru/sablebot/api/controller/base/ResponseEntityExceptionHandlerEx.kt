package ru.sablebot.api.controller.base

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import ru.sablebot.api.dto.ErrorDetailsDto
import ru.sablebot.common.model.exception.NotFoundException

@RestControllerAdvice
class ResponseEntityExceptionHandlerEx : ResponseEntityExceptionHandler() {
    private val logger = KotlinLogging.logger {}

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(): ResponseEntity<Any> =
        ResponseEntity.notFound().build()

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).build()

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(e: Exception): ResponseEntity<ErrorDetailsDto> =
        errorResponse(e, HttpStatus.INTERNAL_SERVER_ERROR)

    protected fun errorResponse(
        e: Exception?,
        status: HttpStatus,
    ): ResponseEntity<ErrorDetailsDto> {
        if (e != null) {
            logger.error(e) { "API error caught: ${e.message}" }
            return response(ErrorDetailsDto(e), status)
        } else {
            logger.error { "Unknown API error caught, $status" }
            return response(null, status)
        }
    }

    private fun <T> response(body: T?, status: HttpStatus): ResponseEntity<T> =
        ResponseEntity(body, HttpHeaders(), status)
}