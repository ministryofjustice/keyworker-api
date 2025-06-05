package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import com.fasterxml.jackson.annotation.JsonAlias
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntry
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerInteraction
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
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
  val staffId: String,
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

fun CaseNote.asKeyworkerInteraction(): KeyworkerInteraction? =
  when (subType) {
    KW_SESSION_SUBTYPE ->
      KeyworkerSession(
        occurredAt,
        personIdentifier,
        staffId.toLong(),
        staffUsername,
        prisonCode,
        createdAt,
        textLength(),
        amendments.size,
        id,
      )

    KW_ENTRY_SUBTYPE ->
      KeyworkerEntry(
        occurredAt,
        personIdentifier,
        staffId.toLong(),
        staffUsername,
        prisonCode,
        createdAt,
        textLength(),
        amendments.size,
        id,
      )

    else -> null
  }
