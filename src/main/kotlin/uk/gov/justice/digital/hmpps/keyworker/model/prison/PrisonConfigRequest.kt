package uk.gov.justice.digital.hmpps.keyworker.model.prison

import jakarta.validation.constraints.Min
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationOrder

data class PrisonConfigRequest(
  val isEnabled: Boolean,
  val allowAutoAllocation: Boolean,
  @field:Min(1, "capacity must be greater than 0") val capacity: Int,
  @field:Min(1, "frequency in weeks must be greater than 0") val frequencyInWeeks: Int,
  val hasPrisonersWithHighComplexityNeeds: Boolean?,
  val allocationOrder: AllocationOrder = AllocationOrder.BY_ALLOCATIONS,
)

data class PrisonConfigResponse(
  val isEnabled: Boolean,
  val hasPrisonersWithHighComplexityNeeds: Boolean,
  val allowAutoAllocation: Boolean,
  val capacity: Int,
  val frequencyInWeeks: Int,
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
