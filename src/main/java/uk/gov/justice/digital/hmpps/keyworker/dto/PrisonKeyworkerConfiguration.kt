package uk.gov.justice.digital.hmpps.keyworker.dto

data class PrisonKeyworkerConfiguration(
  val isEnabled: Boolean,
  val hasPrisonersWithHighComplexityNeeds: Boolean,
  val allowAutoAllocate: Boolean,
  val capacityTier1: Int,
  val capacityTier2: Int?,
  val kwSessionFrequencyInWeeks: Int,
) {
  companion object {
    val NOT_CONFIGURED = PrisonKeyworkerConfiguration(false, false, false, 6, 9, 1)
  }
}
