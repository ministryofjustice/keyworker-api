package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.services.OffenderEventsAdminService

@RestController
@Validated
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${offender-events-sqs.provider}')")
@RequestMapping("/offender-events-queue-admin", produces = [MediaType.APPLICATION_JSON_VALUE])
class OffenderEventsQueueAdmin(
  private val queueAdminService: OffenderEventsAdminService
) {

  @PutMapping("/purge-dlq")
  @PreAuthorize("hasRole('QUEUE_ADMIN')")
  @Operation(
    summary = "Purges the dead letter queue",
    description = "Requires QUEUE_ADMIN role"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role QUEUE_ADMIN")
    ]
  )
  fun purgeEventDlq(): Unit = queueAdminService.clearAllDlqMessages()

  @PutMapping("/transfer-dlq")
  @PreAuthorize("hasRole('QUEUE_ADMIN')")
  @Operation(
    summary = "Transfers all DLQ messages to the main queue",
    description = "Requires QUEUE_ADMIN role"
  )
  @ApiResponses(
    value = [
      ApiResponse(responseCode = "401", description = "Unauthorised, requires a valid Oauth2 token"),
      ApiResponse(responseCode = "403", description = "Forbidden, requires an authorisation with role QUEUE_ADMIN")
    ]
  )
  fun transferEventDlq(): Unit = queueAdminService.transferMessages()
}
