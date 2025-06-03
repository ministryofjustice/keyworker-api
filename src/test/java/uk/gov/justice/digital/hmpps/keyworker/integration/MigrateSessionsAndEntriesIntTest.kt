package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntry
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerInteraction
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asKeyworkerInteraction
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteMigrationInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonReference
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.time.temporal.ChronoUnit
import java.util.UUID

class MigrateSessionsAndEntriesIntTest : IntegrationTest() {
  @Test
  fun `can migrate sessions and entries that do not exist`() {
    val personIdentifier = personIdentifier()
    val caseNotes =
      (0..20).map {
        caseNote(
          personIdentifier,
          now().minusWeeks(it.toLong()),
          if (it % 3 == 0) KW_ENTRY_SUBTYPE else KW_SESSION_SUBTYPE,
        )
      }
    caseNotesMockServer.stubGetKeyworkerCaseNotes(personIdentifier, CaseNotes(caseNotes))

    publishEventToTopic(migrateEvent(personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val sessions = ksRepository.findAll().filter { it.personIdentifier == personIdentifier }
    assertThat(sessions).hasSize(14)

    val entries = keRepository.findAll().filter { it.personIdentifier == personIdentifier }
    assertThat(entries).hasSize(7)

    sessions.forEach { session -> session.verifyAgainst(caseNotes.first { it.id == session.id }) }
    entries.forEach { entry -> entry.verifyAgainst(caseNotes.first { it.id == entry.id }) }
  }

  @Test
  fun `can migrate sessions and entries when some already exist`() {
    val personIdentifier = personIdentifier()
    val caseNotes =
      (0..20).map {
        caseNote(
          personIdentifier,
          now().minusWeeks(it.toLong()),
          if (it % 3 == 0) KW_ENTRY_SUBTYPE else KW_SESSION_SUBTYPE,
        )
      }
    caseNotesMockServer.stubGetKeyworkerCaseNotes(personIdentifier, CaseNotes(caseNotes))
    givenKeyworkerInteraction(caseNotes[3].copy(occurredAt = now().minusWeeks(52)).asKeyworkerInteraction()!!)
    givenKeyworkerInteraction(caseNotes[5].copy(occurredAt = now().minusWeeks(54)).asKeyworkerInteraction()!!)
    givenKeyworkerInteraction(caseNotes[8].copy(occurredAt = now().minusWeeks(56)).asKeyworkerInteraction()!!)
    givenKeyworkerInteraction(caseNotes[9].copy(occurredAt = now().minusWeeks(58)).asKeyworkerInteraction()!!)
    givenKeyworkerInteraction(caseNotes[10].copy(occurredAt = now().minusWeeks(60)).asKeyworkerInteraction()!!)
    givenKeyworkerInteraction(caseNotes[11].copy(occurredAt = now().minusWeeks(62)).asKeyworkerInteraction()!!)

    publishEventToTopic(migrateEvent(personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val sessions = ksRepository.findAll().filter { it.personIdentifier == personIdentifier }
    assertThat(sessions).hasSize(14)

    val entries = keRepository.findAll().filter { it.personIdentifier == personIdentifier }
    assertThat(entries).hasSize(7)

    sessions.forEach { session -> session.verifyAgainst(caseNotes.first { it.id == session.id }) }
    entries.forEach { entry -> entry.verifyAgainst(caseNotes.first { it.id == entry.id }) }
  }

  private fun migrateEvent(personIdentifier: String): HmppsDomainEvent<CaseNoteMigrationInformation> =
    HmppsDomainEvent(
      EventType.MigrateCaseNotes.name,
      CaseNoteMigrationInformation,
      PersonReference.withIdentifier(personIdentifier),
    )

  private fun KeyworkerInteraction.verifyAgainst(note: CaseNote) {
    assertThat(prisonCode).isEqualTo(note.prisonCode)
    assertThat(staffId).isEqualTo(note.staffId.toLong())
    assertThat(staffUsername).isEqualTo(note.staffUsername)
    assertThat(personIdentifier).isEqualTo(note.personIdentifier)
    assertThat(occurredAt.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(note.occurredAt.truncatedTo(ChronoUnit.SECONDS))
    assertThat(createdAt.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(note.createdAt.truncatedTo(ChronoUnit.SECONDS))
    assertThat(id).isEqualTo(note.id)
    assertThat(textLength).isEqualTo(note.textLength())
    assertThat(amendmentCount).isEqualTo(note.amendments.size)
    when (this) {
      is KeyworkerSession -> assertThat(note.subType).isEqualTo(KW_SESSION_SUBTYPE)
      is KeyworkerEntry -> assertThat(note.subType).isEqualTo(KW_ENTRY_SUBTYPE)
    }
  }

  private fun caseNote(
    personIdentifier: String,
    occurredAt: LocalDateTime,
    subType: String,
    type: String = KW_TYPE,
    staffId: Long = NomisIdGenerator.newId(),
    staffUsername: String = NomisIdGenerator.username(),
    prisonCode: String = "MIG",
    createdAt: LocalDateTime = now(),
    text: String = "Som text about the interaction",
    amendments: List<CaseNoteAmendment> = emptyList(),
    id: UUID = IdGenerator.newUuid(),
  ): CaseNote =
    CaseNote(
      id,
      type,
      subType,
      occurredAt,
      personIdentifier,
      staffId.toString(),
      staffUsername,
      prisonCode,
      createdAt,
      text,
      amendments,
    )
}
