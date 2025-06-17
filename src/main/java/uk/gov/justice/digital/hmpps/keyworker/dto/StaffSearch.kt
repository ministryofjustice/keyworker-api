package uk.gov.justice.digital.hmpps.keyworker.dto

data class StaffSearchRequest(
  val query: String?,
  val status: StaffStatus,
  val hasPolicyStaffRole: Boolean?,
)

enum class StaffStatus {
  ALL,
  ACTIVE,
  UNAVAILABLE_ANNUAL_LEAVE,
  UNAVAILABLE_LONG_TERM_ABSENCE,
  UNAVAILABLE_NO_PRISONER_CONTACT,
  INACTIVE,
}

data class StaffSearchResponse(
  val content: List<StaffSearchResult>,
)

data class StaffSearchResult(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val status: CodedDescription,
  val capacity: Int,
  val allocated: Int,
  val allowAutoAllocation: Boolean,
  val numberOfSessions: Int,
  val numberOfEntries: Int,
  val staffRole: StaffRoleInfo?,
  val username: String,
  val email: String?,
)

data class StaffWithRole(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val staffRole: StaffRoleInfo?,
  val username: String,
  val email: String?,
)
