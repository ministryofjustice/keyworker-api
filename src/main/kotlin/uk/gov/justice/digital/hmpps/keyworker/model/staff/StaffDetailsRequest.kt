package uk.gov.justice.digital.hmpps.keyworker.model.staff

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

@Schema(description = "Request to patch the configuration for a staff.")
data class StaffDetailsRequest(
  val status: String,
  val capacity: Int,
  val reactivateOn: LocalDate?,
  val staffRole: StaffRoleRequest,
  val deactivateActiveAllocations: Boolean,
)

data class StaffRoleRequest(
  val position: String,
  val scheduleType: String,
  val hoursPerWeek: BigDecimal,
  val fromDate: LocalDate,
  val toDate: LocalDate?,
)
