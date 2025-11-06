package uk.gov.justice.digital.hmpps.keyworker.controllers

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
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonConfigResponse
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonPolicies
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonStatsService
import java.time.LocalDate

@RestController
@RequestMapping("/prisons/{prisonCode}")
class PrisonController(
  private val prisonService: PrisonService,
  private val statsService: PrisonStatsService,
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
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping(value = ["/policies"])
  fun getPrisonPolicies(
    @PathVariable("prisonCode") prisonCode: String,
  ) = prisonService.getPrisonPolicies(prisonCode)

  @Tag(name = PRISON)
  @CaseloadIdHeader
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @PutMapping(value = ["/policies"])
  fun setPrisonPolicies(
    @PathVariable("prisonCode") prisonCode: String,
    @RequestBody policies: PrisonPolicies,
  ) = prisonService.setPrisonPolicies(prisonCode, policies)

  @PolicyHeader
  @Tag(name = PRISON)
  @PreAuthorize("hasRole('${Roles.ALLOCATIONS_UI}')")
  @GetMapping("/statistics")
  fun getPrisonStatistics(
    @PathVariable prisonCode: String,
    @RequestParam from: LocalDate,
    @RequestParam to: LocalDate,
    @RequestParam comparisonFrom: LocalDate,
    @RequestParam comparisonTo: LocalDate,
  ): PrisonStats = statsService.getPrisonStats(prisonCode, from, to, comparisonFrom, comparisonTo)
}
