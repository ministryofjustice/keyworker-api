package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDateTime

data class PersonStaffAllocationHistory(
  val prisonNumber: String,
  val allocations: List<KeyworkerAllocation>,
)

data class KeyworkerAllocation(
  val active: Boolean,
  val keyworker: Keyworker,
  val prison: CodedDescription,
  val allocated: Actioned,
  val deallocated: Actioned?,
)

data class Keyworker(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
)

data class Actioned(
  val at: LocalDateTime,
  val by: String,
  val reason: CodedDescription,
)
