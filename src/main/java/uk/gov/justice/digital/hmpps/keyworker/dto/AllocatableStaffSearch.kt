package uk.gov.justice.digital.hmpps.keyworker.dto

data class AllocatableSearchRequest(
  val query: String?,
  val status: StaffStatus,
)

data class AllocatableSearchResponse(
  val content: List<AllocatableSummary>,
)

data class AllocatableSummary(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val status: CodedDescription,
  val capacity: Int,
  val allocated: Int,
  val allowAutoAllocation: Boolean,
  val numberOfSessions: Int,
  val numberOfEntries: Int,
  val staffRole: StaffRoleInfo,
)
