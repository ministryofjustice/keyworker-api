package uk.gov.justice.digital.hmpps.keyworker.dto

data class RecommendedAllocations(
  val allocations: List<RecommendedAllocation>,
  val noAvailableKeyworkersFor: List<String>,
)

sealed interface Recommendation {
  val personIdentifier: String
}

data class NoRecommendation(
  override val personIdentifier: String,
) : Recommendation

data class RecommendedAllocation(
  override val personIdentifier: String,
  val keyworker: Keyworker,
) : Recommendation
