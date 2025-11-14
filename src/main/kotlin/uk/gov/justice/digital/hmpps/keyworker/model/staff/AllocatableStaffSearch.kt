package uk.gov.justice.digital.hmpps.keyworker.model.staff

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription

@Schema(description = "Search for allocatable staff")
data class AllocatableSearchRequest(
  @Schema(description = "Query string to search for.")
  val query: String?,
  @Schema(description = "The filter to apply to the results.", defaultValue = "ALL", allowableValues = ["ACTIVE", "INACTIVE", "ALL"])
  val status: StaffStatus,
)

@Schema(description = "A list of allocatable staff members.")
data class AllocatableSearchResponse(
  val content: List<AllocatableSummary>,
)

@Schema(description = "Staff member summary information.")
data class AllocatableSummary(
  @Schema(description = "The staff member's identifier.")
  val staffId: Long,
  @Schema(description = "The staff member's first name.")
  val firstName: String,
  @Schema(description = "The staff member's last name.")
  val lastName: String,
  @Schema(description = "The staff member's current status.")
  val status: CodedDescription,
  @Schema(description = "The staff member's capacity.")
  val capacity: Int,
  @Schema(description = "The number of prisoners currently allocated to this staff member.")
  val allocated: Int,
  @Schema(description = "The details of the staff member's role.")
  val staffRole: StaffRoleInfo,
  @Schema(description = "The statistics for this staff member.")
  val stats: StaffCountStats?,
)
