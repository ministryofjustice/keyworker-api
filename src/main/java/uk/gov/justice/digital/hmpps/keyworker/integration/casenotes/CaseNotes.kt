package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE

data class CaseNotes(
  val content: List<CaseNote>,
)

data class SearchCaseNotes(
  val includeSensitive: Boolean = true,
  val typeSubTypes: Set<TypeSubTypeRequest> = CaseNotesOfInterest.asRequest(),
  val page: Int = 1,
  val size: Int = Int.MAX_VALUE,
  val sort: String = "occurredAt,asc",
)

object CaseNotesOfInterest {
  val entries =
    mapOf(
      KW_TYPE to setOf(KW_SESSION_SUBTYPE, KW_ENTRY_SUBTYPE),
      PO_ENTRY_TYPE to setOf(PO_ENTRY_SUBTYPE),
    )

  fun asRequest() = entries.map { (t, s) -> TypeSubTypeRequest(t, s) }.toSet()

  operator fun get(t: String) = entries[t]
}
