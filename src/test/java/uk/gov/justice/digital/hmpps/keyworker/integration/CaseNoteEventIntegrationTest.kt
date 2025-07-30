package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.data.repository.findByIdOrNull
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationCaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asAllocationCaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteCreated
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteDeleted
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteMoved
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteUpdated
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonReference
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class CaseNoteEventIntegrationTest : IntegrationTest() {
  @ParameterizedTest
  @MethodSource("caseNoteCreated")
  fun `creates a new allocation case note when case note created`(caseNote: CaseNote) {
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteCreated, caseNoteInfo(caseNote), caseNote.personIdentifier)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNotNull()
    acn!!.verifyAgainst(caseNote)

    verifyAudit(acn, acn.id, RevisionType.ADD, setOf(AllocationCaseNote::class.simpleName!!), AllocationContext.get())
  }

  @ParameterizedTest
  @MethodSource("caseNoteUpdated")
  fun `updates allocation case note when case note updated`(caseNote: CaseNote) {
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteUpdated, caseNoteInfo(caseNote), caseNote.personIdentifier)
    val existingOccurredAt = LocalDateTime.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS)
    givenAllocationCaseNote(caseNote.copy(occurredAt = existingOccurredAt).asAllocationCaseNote())
    val existing = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    assertThat(existing!!.occurredAt).isEqualTo(existingOccurredAt)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNotNull()
    acn!!.verifyAgainst(caseNote)

    verifyAudit(acn, acn.id, RevisionType.MOD, setOf(AllocationCaseNote::class.simpleName!!), AllocationContext.get())
  }

  @ParameterizedTest
  @MethodSource("caseNoteMoved")
  fun `moves allocation case note when case note moved`(caseNote: CaseNote) {
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteMoved, caseNoteInfo(caseNote, personIdentifier()), caseNote.personIdentifier)
    givenAllocationCaseNote(
      caseNote
        .copy(personIdentifier = event.additionalInformation.previousNomsNumber!!)
        .asAllocationCaseNote(),
    )
    val existing = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    assertThat(existing!!.personIdentifier).isNotEqualTo(caseNote.personIdentifier)
    assertThat(existing.personIdentifier).isEqualTo(event.additionalInformation.previousNomsNumber)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNotNull()
    acn!!.verifyAgainst(caseNote)

    verifyAudit(acn, acn.id, RevisionType.MOD, setOf(AllocationCaseNote::class.simpleName!!), AllocationContext.get())
  }

  @ParameterizedTest
  @MethodSource("caseNoteDeleted")
  fun `deletes allocation case note when case note deleted`(caseNote: CaseNote) {
    val event = caseNoteEvent(CaseNoteDeleted, caseNoteInfo(caseNote), caseNote.personIdentifier)
    givenAllocationCaseNote(caseNote.asAllocationCaseNote())
    val existing = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    existing!!.verifyAgainst(caseNote)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNull()

    verifyAudit(existing, existing.id, RevisionType.DEL, setOf(AllocationCaseNote::class.simpleName!!), AllocationContext.get())
  }

  @ParameterizedTest
  @MethodSource("caseNoteTypeChanged")
  fun `deletes allocation case note when case note changed to type not of interest`(caseNote: CaseNote) {
    val updateCaseNote = caseNote.copy(type = "ANY", subType = "OTHER")
    caseNotesMockServer.stubGetCaseNote(updateCaseNote)
    val event =
      caseNoteEvent(
        CaseNoteUpdated,
        caseNoteInfo(updateCaseNote),
        caseNote.personIdentifier,
      )
    givenAllocationCaseNote(caseNote.asAllocationCaseNote())
    val existing = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    existing!!.verifyAgainst(caseNote)

    publishEventToTopic(event, updateCaseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNull()

    verifyAudit(existing, existing.id, RevisionType.DEL, setOf(AllocationCaseNote::class.simpleName!!), AllocationContext.get())
  }

  @ParameterizedTest
  @MethodSource("sessionEntrySwap")
  fun `swap session for entry and vice versa`(caseNote: CaseNote) {
    val subType = if (caseNote.subType == KW_SESSION_SUBTYPE) KW_ENTRY_SUBTYPE else KW_SESSION_SUBTYPE
    val updateCaseNote = caseNote.copy(subType = subType)
    caseNotesMockServer.stubGetCaseNote(updateCaseNote)
    val event =
      caseNoteEvent(
        CaseNoteUpdated,
        caseNoteInfo(updateCaseNote),
        caseNote.personIdentifier,
      )
    givenAllocationCaseNote(caseNote.asAllocationCaseNote())
    val existing = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    existing!!.verifyAgainst(caseNote)

    publishEventToTopic(event, updateCaseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = caseNoteRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNotNull()
    assertThat(acn!!.subType).isEqualTo(subType)

    verifyAudit(acn, acn.id, RevisionType.MOD, setOf(AllocationCaseNote::class.simpleName!!), AllocationContext.get())
  }

  @Test
  fun `case notes not of interest are ignored`() {
    val caseNote = caseNote("OTHER", "ANY")
    val event = caseNoteEvent(CaseNoteCreated, caseNoteInfo(caseNote), caseNote.personIdentifier)
    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    assertThat(caseNoteRepository.findByIdOrNull(caseNote.id)).isNull()
  }

  private fun caseNoteInfo(
    caseNote: CaseNote,
    previousNoms: String? = null,
  ) = CaseNoteInformation(caseNote.id, caseNote.type, caseNote.subType, previousNoms)

  private fun caseNoteEvent(
    type: EventType,
    info: CaseNoteInformation,
    personIdentifier: String,
  ): HmppsDomainEvent<CaseNoteInformation> =
    HmppsDomainEvent(
      type.name,
      info,
      PersonReference.withIdentifier(personIdentifier),
    )

  private fun CaseNote.snsAttributes(): Map<String, MessageAttributeValue> =
    mapOf(
      "type" to
        MessageAttributeValue
          .builder()
          .dataType("String")
          .stringValue(type)
          .build(),
      "subType" to
        MessageAttributeValue
          .builder()
          .dataType("String")
          .stringValue(subType)
          .build(),
    )

  companion object {
    private fun caseNote(
      subType: String,
      type: String = KW_TYPE,
      personIdentifier: String = personIdentifier(),
      occurredAt: LocalDateTime = LocalDateTime.now().minusDays(1),
      staffId: Long = NomisIdGenerator.newId(),
      staffUsername: String = NomisIdGenerator.username(),
      prisonCode: String = "LEI",
      createdAt: LocalDateTime = LocalDateTime.now(),
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
      )

    @JvmStatic
    fun caseNoteCreated() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE)),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE)),
      )

    @JvmStatic
    fun caseNoteUpdated() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE)),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE)),
      )

    @JvmStatic
    fun caseNoteMoved() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE)),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE)),
      )

    @JvmStatic
    fun caseNoteDeleted() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE)),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE)),
      )

    @JvmStatic
    fun caseNoteTypeChanged() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE)),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE)),
      )

    @JvmStatic
    fun sessionEntrySwap() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE)),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE)),
      )
  }
}

private fun AllocationCaseNote.verifyAgainst(note: CaseNote) {
  assertThat(prisonCode).isEqualTo(note.prisonCode)
  assertThat(staffId).isEqualTo(note.staffId)
  assertThat(username).isEqualTo(note.staffUsername)
  assertThat(personIdentifier).isEqualTo(note.personIdentifier)
  assertThat(occurredAt.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(note.occurredAt.truncatedTo(ChronoUnit.SECONDS))
  assertThat(id).isEqualTo(note.id)
  assertThat(type).isEqualTo(note.type)
  assertThat(subType).isEqualTo(note.subType)
}
