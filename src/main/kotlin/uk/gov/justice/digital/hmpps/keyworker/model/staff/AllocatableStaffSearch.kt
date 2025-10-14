package uk.gov.justice.digital.hmpps.keyworker.model.staff

import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription

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
  val staffRole: StaffRoleInfo,
  val stats: StaffCountStats?,
)
