package uk.gov.justice.digital.hmpps.keyworker.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Allocation Recommendation")
data class RecommendedAllocations(
  @Schema(description = "The list of recommended allocations.")
  val allocations: List<RecommendedAllocation>,
  @Schema(description = "The list of prisoners that do not have an active key worker.")
  val noAvailableStaffFor: List<String>,
  @Schema(description = "The list of staff members available for allocation.")
  val staff: List<AllocationStaff>,
)

sealed interface Recommendation {
  val personIdentifier: String
}

data class NoRecommendation(
  override val personIdentifier: String,
) : Recommendation

@Schema(description = "The recommended allocation for a prisoner.")
data class RecommendedAllocation(
  @Schema(description = "The prisoner's identifier.")
  override val personIdentifier: String,
  @Schema(description = "The staff member recommended to be allocated to this prisoner.")
  val staff: AllocationStaff,
) : Recommendation

@Schema(description = "Details of a staff member available for allocation.")
data class AllocationStaff(
  @Schema(description = "The staff member's unique identifier.")
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
)
