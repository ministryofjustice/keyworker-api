package uk.gov.justice.digital.hmpps.keyworker.dto

import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import java.time.LocalDate

data class StaffConfigRequest(
  val status: StaffStatus,
  val capacity: Int,
  val deactivateActiveAllocations: Boolean,
  val removeFromAutoAllocation: Boolean,
  val reactivateOn: LocalDate?,
)
