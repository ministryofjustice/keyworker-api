package uk.gov.justice.digital.hmpps.keyworker.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse;
import uk.gov.justice.digital.hmpps.keyworker.exception.AllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotMigratedException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportAutoAllocationException;
import uk.gov.justice.digital.hmpps.keyworker.exception.PrisonNotSupportedException;

import javax.persistence.EntityNotFoundException;


@org.springframework.web.bind.annotation.RestControllerAdvice(
        basePackageClasses = KeyworkerServiceController.class
)
public class ControllerAdvice {

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<byte[]> handleException(RestClientResponseException e) {
        return ResponseEntity
                .status(e.getRawStatusCode())
                .body(e.getResponseBodyAsByteArray());
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleException(RestClientException e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleException(AccessDeniedException e) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.FORBIDDEN.value())
                        .build());
    }

    @ExceptionHandler(AllocationException.class)
    public ResponseEntity<ErrorResponse> handleException(AllocationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .userMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.NOT_FOUND.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(PrisonNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleNotSupportedException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(PrisonNotSupportAutoAllocationException.class)
    public ResponseEntity<ErrorResponse> handleNotSupportedAutoAllocationException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                        .developerMessage(e.getMessage())
                        .build());
    }

    @ExceptionHandler(PrisonNotMigratedException.class)
    public ResponseEntity<ErrorResponse> handleNotMigratedException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse
                        .builder()
                        .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                        .developerMessage(e.getMessage())
                        .build());
    }
}
