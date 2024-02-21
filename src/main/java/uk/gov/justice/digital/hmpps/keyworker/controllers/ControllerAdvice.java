package uk.gov.justice.digital.hmpps.keyworker.controllers;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotMigratedException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportAutoAllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;
import uk.gov.justice.digital.hmpps.keyworker.services.NoContentFoundException;

@org.springframework.web.bind.annotation.RestControllerAdvice(
        basePackageClasses = KeyworkerServiceController.class
)
@Slf4j
public class ControllerAdvice {

    @ExceptionHandler(NoContentFoundException.class)
    public ResponseEntity noContentFoundException(final NoContentFoundException e) {
        return ResponseEntity.noContent().build();
    }


    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<byte[]> handleException(final WebClientResponseException e) {
        log.error("Unexpected exception", e);
        return ResponseEntity
                .status(e.getRawStatusCode())
                .body(e.getResponseBodyAsByteArray());
    }

    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<ErrorResponse> handleException(final WebClientException e) {
        log.error("Unexpected exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleException(final AccessDeniedException e) {
        log.debug("Forbidden (403) returned", e);
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.FORBIDDEN.value())
                        .build());
    }

    @ExceptionHandler(AllocationException.class)
    public ResponseEntity<ErrorResponse> handleException(final AllocationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .userMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(final Exception e) {
        log.error("Unexpected exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler({EntityNotFoundException.class, WebClientResponseException.NotFound.class})
    public ResponseEntity<ErrorResponse> handleNotFoundException(final Exception e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(PrisonNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleNotSupportedException(final Exception e) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                        .developerMessage(e.getMessage())
                        .userMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(PrisonNotSupportAutoAllocationException.class)
    public ResponseEntity<ErrorResponse> handleNotSupportedAutoAllocationException(final Exception e) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(PrisonNotMigratedException.class)
    public ResponseEntity<ErrorResponse> handleNotMigratedException(final Exception e) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                        .developerMessage(e.getMessage())
                        .build());
    }
}
