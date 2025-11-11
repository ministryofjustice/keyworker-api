package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_ALLOCATIONS
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.model.RecommendedAllocations
import uk.gov.justice.digital.hmpps.keyworker.model.person.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.services.AllocationManager
import uk.gov.justice.digital.hmpps.keyworker.services.AllocationRecommender
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestController
@RequestMapping("/prisons/{prisonCode}")
@Tag(name = MANAGE_ALLOCATIONS)
@PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
class ManageAllocationsController(
  private val allocationManager: AllocationManager,
  private val recommend: AllocationRecommender,
) {
  @Operation(
    summary = "Update allocations for a given prison.",
    description = "Update allocations for a given prison."
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Allocations updated"
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
      )
    ]
  )
  @PolicyHeader
  @CaseloadIdHeader
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PutMapping("/prisoners/allocations")
  fun manageAllocations(
    @Parameter(required = true, example = "MDI", description = "The prison's identifier.")
    @PathVariable prisonCode: String,
    @RequestBody psa: PersonStaffAllocations,
  ) {
    allocationManager.manage(prisonCode, psa)
  }

  @Operation(
    summary = "Retrieves a list of recommended allocations for a given prison.",
    description = "Retrieves a list of recommended allocations for a given prison."
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Recommended allocations returned"
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
      )
    ]
  )
  @PolicyHeader
  @GetMapping("/prisoners/allocation-recommendations")
  fun getAllocationRecommendations(
    @Parameter(required = true, example = "MDI", description = "The prison's identifier.")
    @PathVariable prisonCode: String,
  ): RecommendedAllocations = recommend.allocations(prisonCode)
}
