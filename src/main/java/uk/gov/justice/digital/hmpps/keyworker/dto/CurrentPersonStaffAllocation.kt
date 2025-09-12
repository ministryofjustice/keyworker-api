package uk.gov.justice.digital.hmpps.keyworker.dto

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import java.time.LocalDateTime

data class CurrentPersonStaffAllocation(
  val prisonNumber: String,
  val hasHighComplexityOfNeeds: Boolean = false,
  val allocations: List<CurrentAllocation> = listOf(),
  val latestRecordedEvents: List<RecordedEvent> = listOf(),
  val policies: List<PolicyEnabled> = listOf(),
)

data class CurrentAllocation(
  val policy: CodedDescription,
  val prison: CodedDescription,
  val staffMember: CurrentStaffSummary,
)

data class RecordedEvent(
  val prison: CodedDescription,
  val type: RecordedEventType,
  val occurredAt: LocalDateTime,
  val policy: AllocationPolicy,
  val author: Author,
)

data class Author(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val username: String,
)

data class CurrentStaffSummary(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val emailAddresses: Set<String>,
)
