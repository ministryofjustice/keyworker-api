package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate

data class StaffDetails(
  val staffMember: StaffWithRole,
  val status: CodedDescription,
  val prison: CodedDescription,
  val capacity: Int,
  val allocated: Int,
  val allocations: List<Allocation>,
  val stats: StaffStats,
  val allowAutoAllocation: Boolean,
  val reactivateOn: LocalDate?,
)

data class StaffWithRole(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val staffRoleInfo: StaffRoleInfo?,
)

data class Allocation(
  val prisoner: Prisoner,
  val latestSession: LatestSession?,
)

data class Prisoner(
  val prisonNumber: String,
  val firstName: String,
  val lastName: String,
  val csra: String?,
  val cellLocation: String?,
  val releaseDate: LocalDate?,
)

data class StaffStats(
  val current: StaffCountStats,
  val previous: StaffCountStats,
)

data class StaffCountStats(
  val from: LocalDate,
  val to: LocalDate,
  val projectedSessions: Int,
  val recordedSessions: Int,
  val recordedEntries: Int,
  val complianceRate: Double,
)

data class LatestSession(
  val occurredAt: LocalDate,
)
