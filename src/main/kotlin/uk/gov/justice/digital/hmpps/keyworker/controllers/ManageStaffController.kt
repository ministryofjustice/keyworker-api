package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.Operation
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
import uk.gov.justice.digital.hmpps.keyworker.model.staff.JobClassificationResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffDetails
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffDetailsRequest
import uk.gov.justice.digital.hmpps.keyworker.services.staff.GetStaffDetails
import uk.gov.justice.digital.hmpps.keyworker.services.staff.StaffConfigManager
import java.time.LocalDate

@RestController
@RequestMapping("/prisons/{prisonCode}")
@Tag(name = MANAGE_STAFF)
class ManageStaffController(
  private val staffDetails: GetStaffDetails,
  private val staffConfigManager: StaffConfigManager,
) {
  @PolicyHeader
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping("/staff/{staffId}")
  fun getStaffDetails(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
    @RequestParam(required = false) from: LocalDate?,
    @RequestParam(required = false) to: LocalDate?,
    @RequestParam(required = false) comparisonFrom: LocalDate?,
    @RequestParam(required = false) comparisonTo: LocalDate?,
  ): StaffDetails = staffDetails.getDetailsFor(prisonCode, staffId, from, to, comparisonFrom, comparisonTo)

  @Operation(hidden = true)
  @GetMapping("/staff/{staffId}/job-classifications")
  fun getStaffJobClassification(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
  ): JobClassificationResponse = staffDetails.getJobClassificationsFor(prisonCode, staffId)

  @PolicyHeader
  @CaseloadIdHeader
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PutMapping("/staff/{staffId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun modifyStaffDetails(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
    @RequestBody request: StaffDetailsRequest,
  ) {
    staffConfigManager.setStaffDetails(prisonCode, staffId, request)
  }
}