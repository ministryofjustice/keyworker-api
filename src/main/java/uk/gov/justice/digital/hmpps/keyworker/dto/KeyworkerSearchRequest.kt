package uk.gov.justice.digital.hmpps.keyworker.dto

data class KeyworkerSearchRequest(
  val query: String?,
  val status: Status,
) {
  enum class Status {
    ALL,
    ACTIVE,
    UNAVAILABLE_ANNUAL_LEAVE,
    UNAVAILABLE_LONG_TERM_ABSENCE,
    UNAVAILABLE_NO_PRISONER_CONTACT,
    INACTIVE,
  }
}

data class KeyworkerSearchResponse(
  val content: List<KeyworkerSummary>,
)

data class KeyworkerSummary(
  val staffId: Long,
  val firstName: String,
  val lastName: String,
  val status: CodedDescription,
  val capacity: Int,
  val numberAllocated: Int,
  val autoAllocationAllowed: Boolean,
  val numberOfKeyworkerSessions: Int,
)
