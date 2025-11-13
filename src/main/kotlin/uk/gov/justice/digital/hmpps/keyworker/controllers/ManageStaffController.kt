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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_STAFF
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.config.StandardAoiErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.JobClassificationResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffDetails
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffDetailsRequest
import uk.gov.justice.digital.hmpps.keyworker.services.staff.GetStaffDetails
import uk.gov.justice.digital.hmpps.keyworker.services.staff.StaffConfigManager
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate

@RestController
@RequestMapping("/prisons/{prisonCode}")
@Tag(name = MANAGE_STAFF)
class ManageStaffController(
  private val staffDetails: GetStaffDetails,
  private val staffConfigManager: StaffConfigManager,
) {
  @Operation(
    summary = "Retrieve staff details for a specific staff member.",
    description = "Get details and stats for the specified member of prison staff along with their policy staff role and status."
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Staff details and statistics returned"
      ),
      ApiResponse(
        responseCode = "404",
        description = "The staff member associated with this identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ]
  )
  @StandardAoiErrorResponse
  @PolicyHeader
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping("/staff/{staffId}")
  fun getStaffDetails(
    @Parameter(description = "The prison's identifier.", example = "MDI", required = true)
    @PathVariable prisonCode: String,
    @Parameter(description = "The staff member's identifier.", example = "123456", required = true)
    @PathVariable staffId: Long,
    @Parameter(description = "Start date of statistics period in format YYYY-MM-DD.")
    @RequestParam(required = false) from: LocalDate?,
    @Parameter(description = "End date of statistics period in format YYYY-MM-DD.")
    @RequestParam(required = false) to: LocalDate?,
    @Parameter(description = "Start date of statistics period to compare within format YYYY-MM-DD.")
    @RequestParam(required = false) comparisonFrom: LocalDate?,
    @Parameter(description = "End date of statistics period to compare within format YYYY-MM-DD.")
    @RequestParam(required = false) comparisonTo: LocalDate?,
  ): StaffDetails = staffDetails.getDetailsFor(prisonCode, staffId, from, to, comparisonFrom, comparisonTo)

  @Operation(hidden = true)
  @GetMapping("/staff/{staffId}/job-classifications")
  fun getStaffJobClassification(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
  ): JobClassificationResponse = staffDetails.getJobClassificationsFor(prisonCode, staffId)

  @Operation(
    summary = "Update staff details for a specific staff member.",
    description = "Update staff details for a specific staff member."
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Staff details updated"
      ),
      ApiResponse(
        responseCode = "404",
        description = "The staff member associated with this identifier was not found.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ]
  )
  @StandardAoiErrorResponse
  @PolicyHeader
  @CaseloadIdHeader
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PutMapping("/staff/{staffId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun modifyStaffDetails(
    @Parameter(description = "The prison's identifier.", example = "MDI", required = true)
    @PathVariable prisonCode: String,
    @Parameter(description = "The staff member's identifier.", example = "123456", required = true)
    @PathVariable staffId: Long,
    @RequestBody request: StaffDetailsRequest,
  ) {
    staffConfigManager.setStaffDetails(prisonCode, staffId, request)
  }
}