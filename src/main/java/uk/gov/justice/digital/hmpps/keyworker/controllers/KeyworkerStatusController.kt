package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.services.UsernameKeyworker
import uk.gov.justice.digital.hmpps.keyworker.services.VerifyKeyworkerStatus

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
class KeyworkerStatusController(
  private val verify: VerifyKeyworkerStatus,
) {
  @Deprecated(
    message = "Key worker job responsibility can be determined by frontend via hmpps-connect-dps-components"
  )
  @Operation(description = "To determine if a user is a keyworker")
  @ApiResponses(
    value =
      [
        ApiResponse(responseCode = "200", description = "OK - staff recorded verified"),
        ApiResponse(
          responseCode = "400",
          description = "Bad request - username not valid",
          content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
          responseCode = "401",
          description = "Unauthorised",
          content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
          responseCode = "403",
          description = "Forbidden",
          content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
          responseCode = "404",
          description = "Not found - staff not found",
          content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
      ],
  )
  @PreAuthorize("hasAnyRole('${Roles.KEYWORKER_RO}')")
  @GetMapping(path = ["/prisons/{prisonCode}/key-workers/{username}/status"])
  fun userIsKeyworker(
    @PathVariable prisonCode: String,
    @PathVariable username: String,
  ): UsernameKeyworker = verify.isKeyworker(username, prisonCode)
}
