package com.github.stefcool8.executor.config

import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestControllerAdvice
class RestExceptionHandler {

    // Handles validation errors from DTOs (@NotBlank, @DecimalMin, ...)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Invalid value")
        }

        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Validation failed",
            details = errors
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    // Handles invalid sort parameters in Pageable requests
    @ExceptionHandler(InvalidDataAccessApiUsageException::class)
    fun handlePropertyReferenceException(ex: InvalidDataAccessApiUsageException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Invalid sort property: ${
                ex.message?.substringAfter("No property '")?.substringBefore("' found") ?: "Unknown"
            }"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    // Handles intentional HTTP errors thrown in the Service layer (e.g., 404 Not Found)
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = ex.statusCode.value(),
            error = ex.statusCode.toString(),
            message = ex.reason ?: "An error occurred"
        )
        return ResponseEntity.status(ex.statusCode).body(errorResponse)
    }

    // A fallback handler for any unexpected server crashes
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        val errorResponse = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
            message = ex.message ?: "An unexpected error occurred"
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}

data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String,
    val details: Map<String, String>? = null
)
