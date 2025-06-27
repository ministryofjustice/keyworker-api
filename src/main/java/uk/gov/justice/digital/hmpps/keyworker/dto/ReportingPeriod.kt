package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS

data class ReportingPeriod(
  val from: LocalDateTime,
  val to: LocalDateTime,
) {
  fun previousPeriod(): ReportingPeriod =
    ReportingPeriod(
      from.minusDays(DAYS.between(from, to)),
      from,
    )

  companion object {
    fun currentMonth(): ReportingPeriod =
      ReportingPeriod(
        LocalDate.now().minusMonths(1).atStartOfDay(),
        LocalDate.now().plusDays(1).atStartOfDay(),
      )
  }
}
