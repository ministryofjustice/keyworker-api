package uk.gov.justice.digital.hmpps.keyworker.model.person

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PolicyEnabled
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import java.time.LocalDateTime

@Schema(description = "Prisoner Details with current and previous allocations")
data class CurrentPersonStaffAllocation(
  @Schema(description = "The identifier for the prisoner.", example = "A12345")
  val prisonNumber: String,
  @Schema(description = "Indicates if the prisoner has high complexity needs.")
  val hasHighComplexityOfNeeds: Boolean = false,
  @Schema(description = "The list of current allocations for this prisoner.")
  val allocations: List<CurrentAllocation> = listOf(),
  @Schema(description = "The latest recorded events for this prisoner.")
  val latestRecordedEvents: List<RecordedEvent> = listOf(),
  @Schema(description = "The list of prison policies for this prisoner.")
  val policies: List<PolicyEnabled> = listOf(),
)

@Schema(description = "Current allocation details")
data class CurrentAllocation(
  @Schema(description = "Information about the policy in effect at the prison.")
  val policy: CodedDescription,
  @Schema(description = "Information about the prison.")
  val prison: CodedDescription,
  val staffMember: CurrentStaffSummary,
)

@Schema(description = "Recorded event details")
data class RecordedEvent(
  @Schema(description = "The prison where the event occurred.")
  val prison: CodedDescription,
  @Schema(description = "The type of event.")
  val type: RecordedEventType,
  @Schema(description = "The date and time when the event occurred.")
  val occurredAt: LocalDateTime,
  @Schema(description = "The policy tin effect at the prison where the event occurred.")
  val policy: AllocationPolicy,
  @Schema(description = "The details of the user who recorded the event.")
  val author: Author,
)

@Schema(description = "Details of the user who recorded the event.")
data class Author(
  @Schema(description = "The unique identifier for the user.")
  val staffId: Long,
  @Schema(description = "The first name of the user.")
  val firstName: String,
  @Schema(description = "The last name of the user.")
  val lastName: String,
  @Schema(description = "The user's unique username.")
  val username: String,
)

@Schema(description = "Summary of staff details")
data class CurrentStaffSummary(
  @Schema(description = "The unique identifier for the staff member.")
  val staffId: Long,
  @Schema(description = "The first name of the staff member.")
  val firstName: String,
  @Schema(description = "The last name of the staff member.")
  val lastName: String,
  @Schema(description = "The email addresses associated with the staff member.")
  val emailAddresses: Set<String>,
)
