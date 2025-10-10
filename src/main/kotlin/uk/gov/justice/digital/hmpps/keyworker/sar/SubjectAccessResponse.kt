package uk.gov.justice.digital.hmpps.keyworker.sar

import uk.gov.justice.digital.hmpps.keyworker.model.CodedDescription
import java.time.LocalDateTime

data class SubjectAccessResponse(
  val prn: String,
  val content: List<SarAllocation>,
)

data class SarAllocation(
  val allocatedAt: LocalDateTime,
  val allocationExpiredAt: LocalDateTime?,
  val prisonCode: String,
  val allocationReason: String,
  val deallocationReason: String?,
  val staffMember: StaffMember,
  val policy: CodedDescription,
) {
  val activeAllocation: Boolean = allocationExpiredAt == null
}

data class StaffMember(
  val firstName: String,
  val lastName: String,
)
