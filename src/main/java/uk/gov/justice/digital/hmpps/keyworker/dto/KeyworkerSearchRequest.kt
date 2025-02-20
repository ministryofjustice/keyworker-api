package uk.gov.justice.digital.hmpps.keyworker.dto

import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus

data class KeyworkerSearchRequest(
  val query: String?,
  val status: KeyworkerStatus,
)

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
