package uk.gov.justice.digital.hmpps.keyworker.dto

import java.time.LocalDate

data class CurrentPersonStaffAllocation(
  val hasHighComplexityOfNeeds: Boolean,
  val currentKeyworker: CurrentAllocation?,
  val latestSession: LocalDate?,
)

data class CurrentAllocation(
  val keyworker: CurrentKeyworker,
  val prisonCode: String,
)

data class CurrentKeyworker(
  val firstName: String,
  val lastName: String,
)
