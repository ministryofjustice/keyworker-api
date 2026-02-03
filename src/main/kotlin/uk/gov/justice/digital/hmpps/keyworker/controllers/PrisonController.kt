package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.PRISON
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.config.StandardApiErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonConfigResponse
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonPolicies
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonStatsService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate

@RestController
@RequestMapping("/prisons/{prisonCode}")
@Tag(name = PRISON)
@PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
class PrisonController(
  private val prisonService: PrisonService,
  private val statsService: PrisonStatsService,
) {
  @Operation(
    summary = "Update prison configuration for the supplied policy.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Prison configuration updated",
      ),
      ApiResponse(
        responseCode = "404",
        description = "The prison configuration associated with this identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @StandardApiErrorResponse
  @PolicyHeader
  @CaseloadIdHeader
  @PutMapping(value = ["/configurations"])
  fun setPrisonConfiguration(
    @PathVariable @Parameter(required = true, example = "MDI", description = "The identifier of the prison.") prisonCode: String,
    @Valid @RequestBody request: PrisonConfigRequest,
  ): PrisonConfigResponse = prisonService.setPrisonConfig(prisonCode, request)

  @Operation(
    summary = "Retrieve prison configuration for the supplied policy.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Prison configuration returned",
      ),
      ApiResponse(
        responseCode = "404",
        description = "The prison configuration associated with this identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @StandardApiErrorResponse
  @PolicyHeader
  @GetMapping(value = ["/configurations"])
  fun getPrisonConfiguration(
    @PathVariable @Parameter(required = true, example = "MDI", description = "The identifier of the prison.") prisonCode: String,
  ): PrisonConfigResponse = prisonService.getPrisonConfig(prisonCode)

  @Operation(
    summary = "Retrieve policies for a specific prison.",
    description = "Retrieve policies for a specific prison.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Prison policies returned",
      ),
      ApiResponse(
        responseCode = "404",
        description = "The prison policies associated with this identifier were not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @StandardApiErrorResponse
  @GetMapping(value = ["/policies"])
  fun getPrisonPolicies(
    @PathVariable @Parameter(required = true, example = "MDI", description = "The identifier of the prison.") prisonCode: String,
  ) = prisonService.getPrisonPolicies(prisonCode)

  @Operation(
    summary = "Update policies for a specific prison.",
    description = "Update policies for a specific prison.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Prison policies updated",
      ),
      ApiResponse(
        responseCode = "404",
        description = "The prison policies associated with this identifier were not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @StandardApiErrorResponse
  @CaseloadIdHeader
  @PutMapping(value = ["/policies"])
  fun setPrisonPolicies(
    @PathVariable @Parameter(required = true, example = "MDI", description = "The identifier of the prison.") prisonCode: String,
    @RequestBody policies: PrisonPolicies,
  ) = prisonService.setPrisonPolicies(prisonCode, policies)

  @Operation(
    summary = "Retrieve prison statistics for the supplied policy.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Statistics returned",
      ),
    ],
  )
  @StandardApiErrorResponse
  @PolicyHeader
  @GetMapping("/statistics")
  fun getPrisonStatistics(
    @Parameter(required = true, example = "MDI", description = "The identifier of the prison.")
    @PathVariable prisonCode: String,
    @Parameter(required = true, description = "Start date of statistics period in format YYYY-MM-DD.")
    @RequestParam from: LocalDate,
    @Parameter(required = true, description = "End date of statistics period in format YYYY-MM-DD.")
    @RequestParam to: LocalDate,
    @Parameter(required = true, description = "Start date of statistics period to compare with in format YYYY-MM-DD.")
    @RequestParam comparisonFrom: LocalDate,
    @Parameter(required = true, description = "End date of statistics period to compare with in format YYYY-MM-DD.")
    @RequestParam comparisonTo: LocalDate,
  ): PrisonStats = statsService.getPrisonStats(prisonCode, from, to, comparisonFrom, comparisonTo)
}
