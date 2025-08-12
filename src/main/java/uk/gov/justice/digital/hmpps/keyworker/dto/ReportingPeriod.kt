package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.DAYS

data class ReportingPeriod(
  val from: LocalDateTime,
  val to: LocalDateTime,
  val previous: ReportingPeriod? = null,
) {
  fun previousPeriod(): ReportingPeriod =
    previous ?: let {
      val dateRange = DAYS.between(from, to)
      val previousTo = from.minusDays(1)
      val previousFrom = previousTo.minusDays(dateRange)
      return ReportingPeriod(
        previousFrom,
        previousTo,
      )
    }

  companion object {
    fun of(
      from: LocalDate?,
      to: LocalDate?,
      previous: ReportingPeriod? = null,
    ): ReportingPeriod? =
      if (from == null || to == null) {
        null
      } else {
        ReportingPeriod(from.atStartOfDay(), to.atStartOfDay())
      }

    fun currentMonth(): ReportingPeriod =
      ReportingPeriod(
        LocalDate.now().minusMonths(1).atStartOfDay(),
        LocalDate.now().atStartOfDay(),
      )
  }
}
