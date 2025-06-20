package uk.gov.justice.digital.hmpps.keyworker.dto

data class StaffSummary(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
)

data class AllocatableStaff(
  val staffMember: StaffSummary,
  val staffRole: StaffRoleInfo,
)
