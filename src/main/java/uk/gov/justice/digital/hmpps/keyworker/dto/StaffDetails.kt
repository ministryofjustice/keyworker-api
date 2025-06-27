package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate

data class StaffDetails(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val status: CodedDescription,
  val prison: CodedDescription,
  val capacity: Int,
  val allocated: Int,
  val allocations: List<Allocation>,
  val stats: StaffStats,
  val allowAutoAllocation: Boolean,
  val reactivateOn: LocalDate?,
  val staffRole: StaffRoleInfo?,
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
  val relevantAlertCodes: List<String>,
  val remainingAlertCount: Int,
)

data class StaffStats(
  val current: StaffCountStats,
  val previous: StaffCountStats,
)

data class StaffCountStats(
  val from: LocalDate,
  val to: LocalDate,
  val projectedComplianceEvents: Int,
  val recordedComplianceEvents: Int,
  val recordedEvents: List<RecordedEventCount>,
  val complianceRate: Double,
)

data class RecordedEventCount(
  val type: RecordedEventType,
  val count: Int,
)

enum class RecordedEventType {
  SESSION,
  ENTRY,
}

data class LatestSession(
  val occurredAt: LocalDate,
)
