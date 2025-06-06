package uk.gov.justice.digital.hmpps.keyworker.dto

import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary

data class RecommendedAllocations(
  val allocations: List<RecommendedAllocation>,
  val noAvailableStaffFor: List<String>,
)

sealed interface Recommendation {
  val personIdentifier: String
}

data class NoRecommendation(
  override val personIdentifier: String,
) : Recommendation

data class RecommendedAllocation(
  override val personIdentifier: String,
  val staff: StaffSummary,
) : Recommendation
