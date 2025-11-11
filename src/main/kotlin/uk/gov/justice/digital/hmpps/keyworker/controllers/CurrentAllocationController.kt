package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_ALLOCATIONS
import uk.gov.justice.digital.hmpps.keyworker.model.person.CurrentPersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.services.GetCurrentAllocations
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
class CurrentAllocationController(
  private val allocations: GetCurrentAllocations,
) {
  @Operation(
    summary = "Retrieves current allocations for a given prisoner",
    description = "Retrieves current allocations for a given prisoner")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Current allocation returned."
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised, requires a valid Oauth2 token",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden, requires an appropriate role",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "The allocation data associated with this identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ]
  )
  @Tag(name = MANAGE_ALLOCATIONS)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_RO}')")
  @GetMapping("/prisoners/{personIdentifier}/allocations/current")
  fun getCurrentAllocation(
    @Parameter(required = true, example = "A12345", description = "The prisoner's identifier")
    @PathVariable personIdentifier: String,
    @Parameter(required = false, description = "Whether to include contact details in response")
    @RequestParam(required = false, defaultValue = "false") includeContactDetails: Boolean,
  ): CurrentPersonStaffAllocation = allocations.currentFor(personIdentifier, includeContactDetails)
}
