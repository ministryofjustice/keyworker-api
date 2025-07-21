package uk.gov.justice.digital.hmpps.keyworker.integration.casenotes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

class CaseNoteTest {
  @Test
  fun `text length is correctly returned`() {
    val caseNote = caseNote(text = "A".repeat(100))
    assertThat(caseNote.textLength()).isEqualTo(100)

    val withAmendments =
      caseNote(
        text = "A".repeat(150),
        amendments = listOf(CaseNoteAmendment("B".repeat(130)), CaseNoteAmendment("C".repeat(120))),
      )
    assertThat(withAmendments.textLength()).isEqualTo(400)
  }

  private fun caseNote(
    text: String,
    personIdentifier: String = personIdentifier(),
    occurredAt: LocalDateTime = now(),
    subType: String = KW_SESSION_SUBTYPE,
    type: String = KW_TYPE,
    staffId: Long = NomisIdGenerator.newId(),
    staffUsername: String = NomisIdGenerator.username(),
    prisonCode: String = "MIG",
    createdAt: LocalDateTime = now(),
    amendments: List<CaseNoteAmendment> = emptyList(),
    id: UUID = IdGenerator.newUuid(),
  ): CaseNote =
    CaseNote(
      id,
      type,
      subType,
      occurredAt,
      personIdentifier,
      staffId,
      staffUsername,
      prisonCode,
      createdAt,
      text,
      amendments,
    )
}
