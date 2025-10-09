package uk.gov.justice.digital.hmpps.keyworker.model.staff

import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import java.math.BigDecimal
import java.time.LocalDate

data class StaffRoleInfo(
  val position: CodedDescription,
  val scheduleType: CodedDescription,
  val hoursPerWeek: BigDecimal,
  val fromDate: LocalDate,
  val toDate: LocalDate?,
)
