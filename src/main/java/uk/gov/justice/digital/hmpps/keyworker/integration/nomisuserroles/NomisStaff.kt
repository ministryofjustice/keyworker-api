package uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles

data class NomisStaff(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val staffStatus: String,
)

data class NomisStaffMembers(
  val content: List<NomisStaff>,
)
