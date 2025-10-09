package uk.gov.justice.digital.hmpps.keyworker.model.person

import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffAllocationCount

data class PersonSearchRequest(
  val query: String? = null,
  val cellLocationPrefix: String? = null,
  val excludeActiveAllocations: Boolean = false,
)

data class PersonSearchResponse(
  val content: List<PrisonerSummary>,
)

data class PrisonerSummary(
  val personIdentifier: String,
  val firstName: String,
  val lastName: String,
  val location: String?,
  val hasHighComplexityOfNeeds: Boolean,
  val hasAllocationHistory: Boolean,
  val staffMember: StaffAllocationCount?,
  val relevantAlertCodes: List<String>,
  val remainingAlertCount: Int,
)
