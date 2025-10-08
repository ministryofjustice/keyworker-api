package uk.gov.justice.digital.hmpps.keyworker.model

data class RecommendedAllocations(
  val allocations: List<RecommendedAllocation>,
  val noAvailableStaffFor: List<String>,
  val staff: List<AllocationStaff>,
)

sealed interface Recommendation {
  val personIdentifier: String
}

data class NoRecommendation(
  override val personIdentifier: String,
) : Recommendation

data class RecommendedAllocation(
  override val personIdentifier: String,
  val staff: AllocationStaff,
) : Recommendation

data class AllocationStaff(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val status: CodedDescription,
  val allowAutoAllocation: Boolean,
  val capacity: Int,
  val allocated: Int,
)
