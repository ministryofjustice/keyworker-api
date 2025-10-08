package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteRecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asRecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteMigrationInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonReference
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.CaseNotesMockServer.Companion.caseNote
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime.now
import java.time.temporal.ChronoUnit

class MigrateCaseNotesIntTest : IntegrationTest() {
  @Test
  fun `can migrate case notes that do not exist`() {
    val personIdentifier = personIdentifier()
    val kwNotes =
      (0..20).map {
        caseNote(
          if (it % 3 == 0) KW_ENTRY_SUBTYPE else KW_SESSION_SUBTYPE,
          KW_TYPE,
          personIdentifier,
          now().minusWeeks(it.toLong()),
        )
      }

    val poNotes =
      (0..5).map {
        caseNote(
          PO_ENTRY_SUBTYPE,
          PO_ENTRY_TYPE,
          personIdentifier,
          now().minusWeeks(it * 2L),
        )
      }

    caseNotesMockServer.stubSearchCaseNotes(personIdentifier, CaseNotes(kwNotes + poNotes))

    publishEventToTopic(migrateEvent(personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acns =
      AllocationPolicy.entries
        .flatMap {
          setContext(AllocationContext.get().copy(policy = it))
          transactionTemplate.execute {
            recordedEventRepository.findAll()
          }!!
        }.filter { it.personIdentifier == personIdentifier }
    assertThat(acns).hasSize(27)

    val allNotes = kwNotes + poNotes
    acns.forEach { acn -> acn.verifyAgainst(allNotes.first { it.id == acn.id }) }
  }

  @Test
  fun `can migrate case notes when some already exist`() {
    val personIdentifier = personIdentifier()
    val caseNotes =
      (0..20).map {
        caseNote(
          if (it % 3 == 0) KW_ENTRY_SUBTYPE else KW_SESSION_SUBTYPE,
          KW_TYPE,
          personIdentifier,
          now().minusWeeks(it.toLong()),
        )
      }
    transactionTemplate.execute {
      val rdMap = caseNoteRecordedEventRepository.findAll().associateBy { it.key }
      val getType: (String, String) -> CaseNoteRecordedEvent =
        { type, subtype -> requireNotNull(rdMap[CaseNoteTypeKey(type, subtype)]) }
      caseNotesMockServer.stubSearchCaseNotes(personIdentifier, CaseNotes(caseNotes))
      givenRecordedEvent(caseNotes[3].copy(occurredAt = now().minusWeeks(52)).asRecordedEvent(getType))
      givenRecordedEvent(caseNotes[5].copy(occurredAt = now().minusWeeks(54)).asRecordedEvent(getType))
      givenRecordedEvent(caseNotes[8].copy(occurredAt = now().minusWeeks(56)).asRecordedEvent(getType))
      givenRecordedEvent(caseNotes[9].copy(occurredAt = now().minusWeeks(58)).asRecordedEvent(getType))
      givenRecordedEvent(caseNotes[10].copy(occurredAt = now().minusWeeks(60)).asRecordedEvent(getType))
      givenRecordedEvent(caseNotes[11].copy(occurredAt = now().minusWeeks(62)).asRecordedEvent(getType))
    }

    publishEventToTopic(migrateEvent(personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acns = recordedEventRepository.findAll().filter { it.personIdentifier == personIdentifier }
    assertThat(acns).hasSize(21)
    acns.forEach { cn -> cn.verifyAgainst(caseNotes.first { it.id == cn.id }) }
  }

  private fun migrateEvent(personIdentifier: String): HmppsDomainEvent<CaseNoteMigrationInformation> =
    HmppsDomainEvent(
      EventType.MigrateCaseNotes.name,
      CaseNoteMigrationInformation,
      PersonReference.withIdentifier(personIdentifier),
    )

  private fun RecordedEvent.verifyAgainst(note: CaseNote) {
    assertThat(prisonCode).isEqualTo(note.prisonCode)
    assertThat(staffId).isEqualTo(note.staffId)
    assertThat(username).isEqualTo(note.staffUsername)
    assertThat(personIdentifier).isEqualTo(note.personIdentifier)
    assertThat(occurredAt.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(note.occurredAt.truncatedTo(ChronoUnit.SECONDS))
    assertThat(id).isEqualTo(note.id)
    when (note.type to note.subType) {
      KW_TYPE to KW_SESSION_SUBTYPE -> assertThat(type.code).isEqualTo(RecordedEventType.SESSION.name)
      KW_TYPE to KW_ENTRY_SUBTYPE -> assertThat(type.code).isEqualTo(RecordedEventType.ENTRY.name)
      PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE -> assertThat(type.code).isEqualTo(RecordedEventType.ENTRY.name)
    }
  }
}
