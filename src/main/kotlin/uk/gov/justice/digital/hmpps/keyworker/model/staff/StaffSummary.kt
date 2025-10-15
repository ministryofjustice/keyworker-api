package uk.gov.justice.digital.hmpps.keyworker.model.staff

data class StaffSummary(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
)

data class StaffAllocationCount(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val allocationCount: Int,
)

fun StaffSummary?.orDefault(id: Long) = this ?: StaffSummary(id, "", "")
