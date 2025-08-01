package uk.gov.justice.digital.hmpps.keyworker.dto

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import java.time.LocalDateTime

data class CurrentPersonStaffAllocation(
  val prisonNumber: String,
  val hasHighComplexityOfNeeds: Boolean,
  val allocations: List<CurrentAllocation>,
  val latestRecordedEvents: List<RecordedEvent>,
)

data class CurrentAllocation(
  val policy: CodedDescription,
  val prison: CodedDescription,
  val staffMember: StaffSummary,
)

data class RecordedEvent(
  val prison: CodedDescription,
  val type: RecordedEventType,
  val occurredAt: LocalDateTime,
  val policy: AllocationPolicy,
)
