package uk.gov.justice.digital.hmpps.keyworker.controllers

import jakarta.persistence.EntityNotFoundException
import jakarta.validation.ValidationException
import org.apache.commons.lang3.StringUtils
import org.springframework.context.MessageSourceResolvable
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse

@RestControllerAdvice
class ExceptionHandler {
  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.FORBIDDEN).body(
      ErrorResponse(
        HttpStatus.FORBIDDEN.value(),
        "Authentication problem. Check token and roles - ${e.message}",
        e.message,
      ),
    )

  @ExceptionHandler(MissingServletRequestParameterException::class)
  fun handleMissingServletRequestParameterException(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(BAD_REQUEST).body(
      ErrorResponse(
        BAD_REQUEST.value(),
        "Validation failure: ${e.message}",
        e.message,
      ),
    )

  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
    val type = e.requiredType
    val message =
      if (type.isEnum) {
        "Parameter ${e.name} must be one of the following ${StringUtils.join(type.enumConstants, ", ")}"
      } else {
        "Parameter ${e.name} must be of type ${type.typeName}"
      }

    return ResponseEntity.status(BAD_REQUEST).body(
      ErrorResponse(
        BAD_REQUEST.value(),
        "Validation failure: $message",
        e.message,
      ),
    )
  }

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handleHttpMessageNotReadableException(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(BAD_REQUEST).body(
      ErrorResponse(
        BAD_REQUEST.value(),
        "Validation failure: Couldn't read request body",
        e.message,
      ),
    )

  @ExceptionHandler(MethodArgumentNotValidException::class)
  fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> = e.allErrors.mapErrors()

  @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
  fun handleIllegalArgumentOrStateException(e: RuntimeException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          BAD_REQUEST.value(),
          "Validation failure: ${e.message}",
          e.devMessage(),
        ),
      )

  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> =
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          BAD_REQUEST.value(),
          "Validation failure: ${e.message}",
          e.devMessage(),
        ),
      )

  @ExceptionHandler(HandlerMethodValidationException::class)
  fun handleHandlerMethodValidationException(e: HandlerMethodValidationException): ResponseEntity<ErrorResponse> = e.allErrors.mapErrors()

  @ExceptionHandler(value = [NoResourceFoundException::class, EntityNotFoundException::class, WebClientResponseException.NotFound::class])
  fun handleNoResourceFoundException(e: Exception): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(NOT_FOUND).body(
      ErrorResponse(
        NOT_FOUND.value(),
        "No resource found failure: ${e.message}",
        e.message,
      ),
    )

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(INTERNAL_SERVER_ERROR).body(
      ErrorResponse(
        INTERNAL_SERVER_ERROR.value(),
        "An unexpected server error occurred.",
        "${e::class.simpleName} => ${e.message}",
      ),
    )

  private fun List<MessageSourceResolvable>.mapErrors() =
    map { it.defaultMessage }.distinct().sorted().let {
      val validationFailure = "Validation failure"
      val message =
        if (it.size > 1) {
          """
              |${validationFailure}s: 
              |${it.joinToString(System.lineSeparator())}
              |
          """.trimMargin()
        } else {
          "$validationFailure: ${it.joinToString(System.lineSeparator())}"
        }
      ResponseEntity
        .status(BAD_REQUEST)
        .body(
          ErrorResponse(
            BAD_REQUEST.value(),
            message,
            "400 BAD_REQUEST $message",
          ),
        )
    }

  @ExceptionHandler(WebClientResponseException::class)
  fun handleException(e: WebClientResponseException): ResponseEntity<ByteArray> =
    ResponseEntity
      .status(e.statusCode.value())
      .body(e.responseBodyAsByteArray)

  private fun RuntimeException.devMessage(): String = message ?: "${this::class.simpleName}: ${cause?.message ?: ""}"
}
