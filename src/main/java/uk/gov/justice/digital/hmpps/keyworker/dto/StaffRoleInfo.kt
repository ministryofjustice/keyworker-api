package uk.gov.justice.digital.hmpps.keyworker.dto

import java.math.BigDecimal
import java.time.LocalDate

data class StaffRoleInfo(
  val position: CodedDescription,
  val scheduleType: CodedDescription,
  val hoursPerWeek: BigDecimal,
  val fromDate: LocalDate,
  val toDate: LocalDate?,
)
