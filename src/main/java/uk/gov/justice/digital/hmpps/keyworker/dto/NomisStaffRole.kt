package uk.gov.justice.digital.hmpps.keyworker.dto

import java.math.BigDecimal
import java.time.LocalDate

data class NomisStaffRole(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val position: String,
  val scheduleType: String,
  val hoursPerWeek: BigDecimal,
  val fromDate: LocalDate,
  val toDate: LocalDate?,
) {
  fun isExpired(): Boolean = toDate?.isAfter(LocalDate.now()) == false
}
