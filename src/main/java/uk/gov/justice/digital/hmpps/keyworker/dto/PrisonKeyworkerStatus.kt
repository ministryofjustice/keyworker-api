package uk.gov.justice.digital.hmpps.keyworker.dto

data class PrisonKeyworkerStatus(
  val isEnabled: Boolean,
  val hasPrisonersWithHighComplexityNeeds: Boolean,
) {
  companion object {
    val NOT_CONFIGURED = PrisonKeyworkerStatus(false, false)
  }
}
