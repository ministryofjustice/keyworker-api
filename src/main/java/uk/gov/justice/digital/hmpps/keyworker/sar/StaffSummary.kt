package uk.gov.justice.digital.hmpps.keyworker.sar

data class StaffSummary(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val scheduleType: String,
)
