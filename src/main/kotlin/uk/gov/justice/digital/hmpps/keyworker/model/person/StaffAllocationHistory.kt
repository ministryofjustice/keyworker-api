package uk.gov.justice.digital.hmpps.keyworker.model.person

import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary
import java.time.LocalDateTime

data class StaffAllocationHistory(
  val prisonNumber: String,
  val allocations: List<StaffAllocation>,
)

data class StaffAllocation(
  val active: Boolean,
  val staffMember: StaffSummary,
  val prison: CodedDescription,
  val allocated: Actioned,
  val deallocated: Actioned?,
)

data class Actioned(
  val at: LocalDateTime,
  val by: String,
  val reason: CodedDescription,
)
