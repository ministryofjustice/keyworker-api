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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_ALLOCATIONS
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.config.StandardApiErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.model.person.StaffAllocationHistory
import uk.gov.justice.digital.hmpps.keyworker.services.GetAllocations
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@RequestMapping("/prisoners/{prisonNumber}")
class GetAllocationsController(
  private val allocations: GetAllocations,
) {
  @Operation(
    summary = "Retrieves current allocations for a given prisoner",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Allocation history returned.",
      ),
      ApiResponse(
        responseCode = "404",
        description = "The allocation history associated with this identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @StandardApiErrorResponse
  @PolicyHeader
  @Tag(name = MANAGE_ALLOCATIONS)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping("/allocations")
  fun getAllocations(
    @Parameter(required = true, example = "A12345", description = "The prisoners identifier.")
    @PathVariable prisonNumber: String,
  ): StaffAllocationHistory = allocations.historyFor(prisonNumber)
}
