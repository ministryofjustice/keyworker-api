package uk.gov.justice.digital.hmpps.keyworker.dto

data class KeyworkerAllocationHistory(
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
