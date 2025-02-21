package uk.gov.justice.digital.hmpps.keyworker.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonService
import java.time.LocalDate

@Tag(name = "Allocate Keyworkers")
@RestController
@RequestMapping(value = ["/prisons/{prisonCode}/keyworker"])
class PrisonController(
  private val prisonService: PrisonService,
) {
  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @GetMapping("/configuration")
  fun getPrisonConfiguration(
    @PathVariable("prisonCode") prisonCode: String,
  ): PrisonKeyworkerConfiguration = prisonService.getPrisonKeyworkerStatus(prisonCode)

  @PreAuthorize("hasRole('${Roles.KEYWORKER_RO}')")
  @GetMapping("/statistics")
  fun getPrisonStatistics(
    @PathVariable prisonCode: String,
    @RequestParam from: LocalDate,
    @RequestParam to: LocalDate,
  ): PrisonStats = prisonService.getPrisonStats(prisonCode, from, to)
}
