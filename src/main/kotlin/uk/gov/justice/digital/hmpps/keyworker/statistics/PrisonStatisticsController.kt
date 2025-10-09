package uk.gov.justice.digital.hmpps.keyworker.statistics

import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/prison-statistics")
class PrisonStatisticsController(
  private val prisonStats: PrisonStatisticsTrigger,
) {
  @Operation(hidden = true)
  @PostMapping("/calculate")
  fun calculatePrisonStats(
    @RequestParam(required = false) date: LocalDate?,
  ) {
    prisonStats.runFor(date ?: LocalDate.now().minusDays(1))
  }
}
