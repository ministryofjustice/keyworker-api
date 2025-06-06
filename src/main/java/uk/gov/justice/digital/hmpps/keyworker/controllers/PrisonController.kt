package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_KEYWORKERS
import uk.gov.justice.digital.hmpps.keyworker.config.MANAGE_STAFF
import uk.gov.justice.digital.hmpps.keyworker.config.PRISON
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDetails
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonConfigResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffDetails
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.services.GetKeyworkerDetails
import uk.gov.justice.digital.hmpps.keyworker.services.GetStaffDetails
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonStatsService
import uk.gov.justice.digital.hmpps.keyworker.services.StaffConfigManager
import java.time.LocalDate

@RestController
@RequestMapping("/prisons/{prisonCode}")
class PrisonController(
  private val prisonService: PrisonService,
  private val statsService: PrisonStatsService,
  private val keyworkerDetails: GetKeyworkerDetails,
  private val staffDetails: GetStaffDetails,
  private val staffConfigManager: StaffConfigManager,
) {
  @PolicyHeader
  @CaseloadIdHeader
  @Tag(name = PRISON)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PutMapping(value = ["/configurations"])
  fun setPrisonConfiguration(
    @PathVariable("prisonCode") prisonCode: String,
    @Valid @RequestBody request: PrisonConfigRequest,
  ): PrisonConfigResponse = prisonService.setPrisonConfig(prisonCode, request)

  @PolicyHeader
  @Tag(name = PRISON)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping(value = ["/configurations"])
  fun getPrisonConfiguration(
    @PathVariable("prisonCode") prisonCode: String,
  ): PrisonConfigResponse = prisonService.getPrisonConfig(prisonCode)

  @Tag(name = PRISON)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @GetMapping(value = ["/configuration/keyworker"])
  fun getPrisonKeyworkerConfiguration(
    @PathVariable("prisonCode") prisonCode: String,
  ): PrisonKeyworkerConfiguration = prisonService.getPrisonKeyworkerConfig(prisonCode)

  @Tag(name = PRISON)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @GetMapping(value = ["/statistics/keyworker"])
  fun getPrisonStatistics(
    @PathVariable prisonCode: String,
    @RequestParam from: LocalDate,
    @RequestParam to: LocalDate,
  ): PrisonStats = statsService.getPrisonStats(prisonCode, from, to)

  @Tag(name = MANAGE_KEYWORKERS)
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @GetMapping("/keyworkers/{staffId}")
  fun getKeyworkerDetails(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
  ): KeyworkerDetails = keyworkerDetails.getFor(prisonCode, staffId)

  @PolicyHeader
  @Tag(name = MANAGE_STAFF)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping("/staff/{staffId}")
  fun getStaffDetails(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
  ): StaffDetails = staffDetails.getFor(prisonCode, staffId)

  @Tag(name = MANAGE_KEYWORKERS)
  @CaseloadIdHeader
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RW}')")
  @PutMapping("/keyworkers/{staffId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun modifyKeyworkerConfig(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
    @RequestBody request: StaffConfigRequest,
  ) {
    staffConfigManager.setStaffConfiguration(prisonCode, staffId, request)
  }

  @PolicyHeader
  @CaseloadIdHeader
  @Tag(name = MANAGE_STAFF)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PutMapping("/staff/{staffId}/configuration")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun modifyStaffConfig(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
    @RequestBody request: StaffConfigRequest,
  ) {
    staffConfigManager.setStaffConfiguration(prisonCode, staffId, request)
  }

  @PolicyHeader
  @CaseloadIdHeader
  @Tag(name = MANAGE_STAFF)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PutMapping("/staff/{staffId}/job-classification")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun modifyStaffJob(
    @PathVariable prisonCode: String,
    @PathVariable staffId: Long,
    @RequestBody request: StaffJobClassificationRequest,
  ) {
    staffConfigManager.setStaffJobClassification(prisonCode, staffId, request)
  }
}
