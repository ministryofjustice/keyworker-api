package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE

data class CaseNotes(
  val content: List<CaseNote>,
)

data class SearchCaseNotes(
  val includeSensitive: Boolean = true,
  val typeSubTypes: Set<TypeSubTypeRequest> = setOf(TypeSubTypeRequest(KW_TYPE, setOf(KW_SESSION_SUBTYPE, KW_ENTRY_SUBTYPE))),
  val page: Int = 1,
  val size: Int = Int.MAX_VALUE,
  val sort: String = "occurredAt,asc",
)
