package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import com.fasterxml.jackson.annotation.JsonAlias
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationCaseNote
import java.time.LocalDateTime
import java.util.UUID

data class CaseNote(
  @JsonAlias("caseNoteId")
  val id: UUID,
  val type: String,
  val subType: String,
  @JsonAlias("occurrenceDateTime")
  val occurredAt: LocalDateTime,
  @JsonAlias("offenderIdentifier")
  val personIdentifier: String,
  @JsonAlias("authorUserId")
  val staffId: Long,
  @JsonAlias("authorUsername")
  val staffUsername: String,
  @JsonAlias("locationId")
  val prisonCode: String,
  @JsonAlias("creationDateTime")
  val createdAt: LocalDateTime,
  val text: String,
  val amendments: List<CaseNoteAmendment>,
) {
  fun textLength() = amendments.sumOf { it.text.length } + text.length

  companion object {
    const val KW_TYPE = "KA"
    const val KW_SESSION_SUBTYPE = "KS"
    const val KW_ENTRY_SUBTYPE = "KE"
    const val PO_ENTRY_TYPE = "REPORT"
    const val PO_ENTRY_SUBTYPE = "POE"
  }
}

data class CaseNoteAmendment(
  @JsonAlias("additionalNoteText")
  val text: String,
)

fun CaseNote.asAllocationCaseNote(): AllocationCaseNote =
  AllocationCaseNote(prisonCode, personIdentifier, staffId, staffUsername, type, subType, occurredAt, id)
