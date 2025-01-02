package uk.gov.justice.digital.hmpps.keyworker.sar

import java.time.LocalDateTime

data class SubjectAccessResponse(val prn: String, val content: List<SarKeyWorker>)

data class SarKeyWorker(
  val allocatedAt: LocalDateTime,
  val allocationExpiredAt: LocalDateTime?,
  val prisonCode: String,
  val allocationType: String,
  val allocationReason: String,
  val deallocationReason: String?,
  val keyworker: StaffMember,
) {
  val activeAllocation: Boolean = allocationExpiredAt == null
}

data class StaffMember(val firstName: String, val lastName: String)
