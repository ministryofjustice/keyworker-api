package uk.gov.justice.digital.hmpps.keyworker.model.person

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary
import java.time.LocalDateTime

@Schema(description = "Prisoner allocation history details.")
data class StaffAllocationHistory(
  @Schema(description = "The identifier for the prisoner.", example = "A12345")
  val prisonNumber: String,
  @Schema(description = "The list of allocations for this prisoner.")
  val allocations: List<StaffAllocation>,
)

@Schema(description = "Staff allocation details.")
data class StaffAllocation(
  @Schema(description = "Indicates if the staff member is currently assigned to this prisoner.")
  val active: Boolean,
  @Schema(description = "The details of the staff member.")
  val staffMember: StaffSummary,
  @Schema(description = "The prison where the staff member is assigned to this prisoner.")
  val prison: CodedDescription,
  @Schema(description = "The details regarding when the staff member was allocated to this prisoner.")
  val allocated: Actioned,
  @Schema(description = "The details regarding when the staff member was de-allocated from this prisoner.")
  val deallocated: Actioned?,
)

@Schema(description = "Details relating to when an action was performed.")
data class Actioned(
  @Schema(description = "The date and time when the action was performed.")
  val at: LocalDateTime,
  @Schema(description = "The name of the user who performed the action.")
  val by: String,
  @Schema(description = "The reason for the action.")
  val reason: CodedDescription,
)
