package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class ReportingPeriod(
  val from: LocalDateTime,
  val to: LocalDateTime,
) {
  fun minusMonths(monthsToSubtract: Long): ReportingPeriod =
    ReportingPeriod(
      from.minusMonths(monthsToSubtract),
      to.minusMonths(monthsToSubtract),
    )

  companion object {
    fun currentMonth(): ReportingPeriod =
      ReportingPeriod(
        LocalDate.now().minusMonths(1).atStartOfDay(),
        LocalDate.now().plusDays(1).atStartOfDay(),
      )
  }
}
