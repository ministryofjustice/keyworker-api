package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS

data class ReportingPeriod(
  val from: LocalDateTime,
  val to: LocalDateTime,
) {
  fun previousPeriod(): ReportingPeriod {
    val dateRange = DAYS.between(from, to)
    val previousTo = from.minusDays(1)
    val previousFrom = previousTo.minusDays(dateRange)
    return ReportingPeriod(
      previousFrom,
      previousTo,
    )
  }

  companion object {
    fun currentMonth(): ReportingPeriod =
      ReportingPeriod(
        LocalDate.now().minusMonths(1).atStartOfDay(),
        LocalDate.now().atStartOfDay(),
      )
  }
}
