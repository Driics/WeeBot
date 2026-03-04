package ru.sablebot.api.controller.base

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import ru.sablebot.api.dto.ErrorDetailsDto
import ru.sablebot.common.model.exception.NotFoundException

@RestControllerAdvice
class ResponseEntityExceptionHandlerEx : ResponseEntityExceptionHandler() {
    private val log = KotlinLogging.logger {}

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(): ResponseEntity<ErrorDetailsDto> =
        response(ErrorDetailsDto(error = "NOT_FOUND", description = "Resource not found"), HttpStatus.NOT_FOUND)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(): ResponseEntity<ErrorDetailsDto> =
        response(ErrorDetailsDto(error = "ACCESS_DENIED", description = "Access denied"), HttpStatus.FORBIDDEN)

    @ExceptionHandler(NumberFormatException::class)
    fun handleNumberFormat(e: NumberFormatException): ResponseEntity<ErrorDetailsDto> =
        response(ErrorDetailsDto(error = "BAD_REQUEST", description = "Invalid guild ID format"), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(e: Exception): ResponseEntity<ErrorDetailsDto> {
        log.error(e) { "API error caught: ${e.message}" }
        return response(
            ErrorDetailsDto(error = "INTERNAL_ERROR", description = "An internal error occurred"),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }

    private fun response(body: ErrorDetailsDto, status: HttpStatus): ResponseEntity<ErrorDetailsDto> =
        ResponseEntity(body, HttpHeaders(), status)
}
