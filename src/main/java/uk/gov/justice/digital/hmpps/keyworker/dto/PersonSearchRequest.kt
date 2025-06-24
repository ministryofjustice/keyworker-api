package uk.gov.justice.digital.hmpps.keyworker.dto

import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary

data class PersonSearchRequest(
  val query: String? = null,
  val cellLocationPrefix: String? = null,
  val excludeActiveAllocations: Boolean = false,
)

data class PersonSearchResponse(
  val content: List<PrisonerSummaryWithAlertDetails>,
)

data class PrisonerSummary(
  val personIdentifier: String,
  val firstName: String,
  val lastName: String,
  val location: String?,
  val hasHighComplexityOfNeeds: Boolean,
  val hasAllocationHistory: Boolean,
  val staffMember: StaffSummary?,
)

data class PrisonerSummaryWithAlertDetails(
  val personIdentifier: String,
  val firstName: String,
  val lastName: String,
  val location: String?,
  val hasHighComplexityOfNeeds: Boolean,
  val hasAllocationHistory: Boolean,
  val staffMember: StaffSummary?,
  val alerts: List<AlertDetails>,
)