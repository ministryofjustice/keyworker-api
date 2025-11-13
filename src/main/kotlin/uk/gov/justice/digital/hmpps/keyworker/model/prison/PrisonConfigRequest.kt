package uk.gov.justice.digital.hmpps.keyworker.model.prison

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationOrder

@Schema(description = "Request body for updating a prison's key worker configuration.")
data class PrisonConfigRequest(
  @Schema(description = "Indicates whether the key worker feature is enabled.", required = true)
  val isEnabled: Boolean,
  @Schema(description = "Indicates whether the auto allocation of key workers is enabled.", required = true)
  val allowAutoAllocation: Boolean,
  @Schema(description = "The maximum number of prisoners that can be allocated to a key worker.", required = true, minimum = "1")
  @field:Min(1, "capacity must be greater than 0") val capacity: Int,
  @Schema(
    description = "The frequency in weeks that key workers should have a session with their allocated prisoners.",
    required = true,
    minimum = "1",
  )
  @field:Min(1, "frequency in weeks must be greater than 0") val frequencyInWeeks: Int,
  @Schema(description = "Indicates whether the prison has any prisoners with high complexity needs.")
  val hasPrisonersWithHighComplexityNeeds: Boolean?,
  @Schema(description = "The sort order used for displaying key workers when assigning to prisoners.")
  val allocationOrder: AllocationOrder = AllocationOrder.BY_ALLOCATIONS,
)

data class PrisonConfigResponse(
  @Schema(description = "Indicates whether the key worker feature is enabled.")
  val isEnabled: Boolean,
  @Schema(description = "Indicates whether the prison has any prisoners with high complexity needs.")
  val hasPrisonersWithHighComplexityNeeds: Boolean,
  @Schema(description = "Indicates whether the auto allocation of key workers is enabled.")
  val allowAutoAllocation: Boolean,
  @Schema(description = "The maximum number of prisoners that can be allocated to a key worker.")
  val capacity: Int,
  @Schema(description = "The frequency in weeks that key workers should have a session with their allocated prisoners.")
  val frequencyInWeeks: Int,
  @Schema(description = "The sort order used for displaying key workers when assigning to prisoners.")
  val allocationOrder: AllocationOrder,
) {
  companion object {
    val DEFAULT =
      PrisonConfigResponse(
        isEnabled = false,
        hasPrisonersWithHighComplexityNeeds = false,
        allowAutoAllocation = false,
        capacity = 9,
        frequencyInWeeks = 1,
        allocationOrder = AllocationOrder.BY_ALLOCATIONS,
      )
  }
}
