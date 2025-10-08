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
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.CaseNoteTypeKey
import uk.gov.justice.digital.hmpps.keyworker.domain.RecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.dto.staff.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asRecordedEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.CaseNoteInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteCreated
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteDeleted
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteMoved
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType.CaseNoteUpdated
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonReference
import uk.gov.justice.digital.hmpps.keyworker.integration.wiremock.CaseNotesMockServer.Companion.caseNote
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class CaseNoteEventIntegrationTest : IntegrationTest() {
  @ParameterizedTest
  @MethodSource("caseNoteCreated")
  fun `creates a new recorded event when case note created`(
    caseNote: CaseNote,
    policy: AllocationPolicy,
  ) {
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteCreated, caseNoteInfo(caseNote), caseNote.personIdentifier)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    setContext(AllocationContext.get().copy(policy = policy))
    val acn = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNotNull()
    acn!!.verifyAgainst(caseNote)

    verifyAudit(acn, acn.id, RevisionType.ADD, setOf(RecordedEvent::class.simpleName!!), AllocationContext.get())
  }

  @ParameterizedTest
  @MethodSource("caseNoteUpdated")
  fun `updates recorded event when case note updated`(
    caseNote: CaseNote,
    policy: AllocationPolicy,
  ) {
    setContext(AllocationContext.get().copy(policy = policy))
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteUpdated, caseNoteInfo(caseNote), caseNote.personIdentifier)
    val existingOccurredAt = LocalDateTime.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS)
    givenRecordedEvent(
      caseNote.copy(occurredAt = existingOccurredAt).asRecordedEvent { type, code ->
        requireNotNull(caseNoteRecordedEventRepository.findByKey(CaseNoteTypeKey(type, code)))
      },
    )
    val existing = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    assertThat(existing!!.occurredAt).isEqualTo(existingOccurredAt)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNotNull()
    acn!!.verifyAgainst(caseNote)

    verifyAudit(acn, acn.id, RevisionType.MOD, setOf(RecordedEvent::class.simpleName!!), AllocationContext.get())
  }

  @ParameterizedTest
  @MethodSource("caseNoteMoved")
  fun `moves recorded event when case note moved`(
    caseNote: CaseNote,
    policy: AllocationPolicy,
  ) {
    setContext(AllocationContext.get().copy(policy = policy))
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteMoved, caseNoteInfo(caseNote, personIdentifier()), caseNote.personIdentifier)
    givenRecordedEvent(
      caseNote.copy(personIdentifier = event.additionalInformation.previousNomsNumber!!).asRecordedEvent { type, code ->
        requireNotNull(caseNoteRecordedEventRepository.findByKey(CaseNoteTypeKey(type, code)))
      },
    )
    val existing = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    assertThat(existing!!.personIdentifier).isNotEqualTo(caseNote.personIdentifier)
    assertThat(existing.personIdentifier).isEqualTo(event.additionalInformation.previousNomsNumber)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNotNull()
    acn!!.verifyAgainst(caseNote)

    verifyAudit(acn, acn.id, RevisionType.MOD, setOf(RecordedEvent::class.simpleName!!), AllocationContext.get())
  }

  @ParameterizedTest
  @MethodSource("caseNoteDeleted")
  fun `deletes recorded event when case note deleted`(
    caseNote: CaseNote,
    policy: AllocationPolicy,
  ) {
    setContext(AllocationContext.get().copy(policy = policy))
    val event = caseNoteEvent(CaseNoteDeleted, caseNoteInfo(caseNote), caseNote.personIdentifier)
    givenRecordedEvent(
      caseNote.asRecordedEvent { type, code ->
        requireNotNull(caseNoteRecordedEventRepository.findByKey(CaseNoteTypeKey(type, code)))
      },
    )
    val existing = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    existing!!.verifyAgainst(caseNote)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNull()

    verifyAudit(
      existing,
      existing.id,
      RevisionType.DEL,
      setOf(RecordedEvent::class.simpleName!!),
      AllocationContext.get(),
    )
  }

  @ParameterizedTest
  @MethodSource("caseNoteTypeChanged")
  fun `deletes recorded event when case note changed to type not of interest`(
    caseNote: CaseNote,
    policy: AllocationPolicy,
  ) {
    setContext(AllocationContext.get().copy(policy = policy))
    val updateCaseNote = caseNote.copy(type = "ANY", subType = "OTHER")
    caseNotesMockServer.stubGetCaseNote(updateCaseNote)
    val event =
      caseNoteEvent(
        CaseNoteUpdated,
        caseNoteInfo(updateCaseNote),
        caseNote.personIdentifier,
      )
    givenRecordedEvent(
      caseNote.asRecordedEvent { type, code ->
        requireNotNull(caseNoteRecordedEventRepository.findByKey(CaseNoteTypeKey(type, code)))
      },
    )
    val existing = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    existing!!.verifyAgainst(caseNote)

    publishEventToTopic(event, updateCaseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNull()

    verifyAudit(
      existing,
      existing.id,
      RevisionType.DEL,
      setOf(RecordedEvent::class.simpleName!!),
      AllocationContext.get(),
    )
  }

  @ParameterizedTest
  @MethodSource("sessionEntrySwap")
  fun `swap session for entry and vice versa`(
    caseNote: CaseNote,
    policy: AllocationPolicy,
  ) {
    setContext(AllocationContext.get().copy(policy = policy))
    val subType = if (caseNote.subType == KW_SESSION_SUBTYPE) KW_ENTRY_SUBTYPE else KW_SESSION_SUBTYPE
    val updateCaseNote = caseNote.copy(subType = subType)
    caseNotesMockServer.stubGetCaseNote(updateCaseNote)
    val event =
      caseNoteEvent(
        CaseNoteUpdated,
        caseNoteInfo(updateCaseNote),
        caseNote.personIdentifier,
      )
    givenRecordedEvent(
      caseNote.asRecordedEvent { type, code ->
        requireNotNull(
          caseNoteRecordedEventRepository.findByKey(
            CaseNoteTypeKey(
              type,
              code,
            ),
          ),
        )
      },
    )
    val existing = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(existing).isNotNull()
    existing!!.verifyAgainst(caseNote)

    publishEventToTopic(event, updateCaseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val acn = recordedEventRepository.findByIdOrNull(caseNote.id)
    assertThat(acn).isNotNull()

    verifyAudit(acn!!, acn.id, RevisionType.MOD, setOf(RecordedEvent::class.simpleName!!), AllocationContext.get())
  }

  @Test
  fun `case notes not of interest are ignored`() {
    val caseNote = caseNote("OTHER", "ANY")
    val event = caseNoteEvent(CaseNoteCreated, caseNoteInfo(caseNote), caseNote.personIdentifier)
    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    assertThat(recordedEventRepository.findByIdOrNull(caseNote.id)).isNull()
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
    @JvmStatic
    fun caseNoteCreated() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(PO_ENTRY_SUBTYPE, PO_ENTRY_TYPE), AllocationPolicy.PERSONAL_OFFICER),
      )

    @JvmStatic
    fun caseNoteUpdated() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(PO_ENTRY_SUBTYPE, PO_ENTRY_TYPE), AllocationPolicy.PERSONAL_OFFICER),
      )

    @JvmStatic
    fun caseNoteMoved() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(PO_ENTRY_SUBTYPE, PO_ENTRY_TYPE), AllocationPolicy.PERSONAL_OFFICER),
      )

    @JvmStatic
    fun caseNoteDeleted() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(PO_ENTRY_SUBTYPE, PO_ENTRY_TYPE), AllocationPolicy.PERSONAL_OFFICER),
      )

    @JvmStatic
    fun caseNoteTypeChanged() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(PO_ENTRY_SUBTYPE, PO_ENTRY_TYPE), AllocationPolicy.PERSONAL_OFFICER),
      )

    @JvmStatic
    fun sessionEntrySwap() =
      listOf(
        Arguments.of(caseNote(KW_SESSION_SUBTYPE), AllocationPolicy.KEY_WORKER),
        Arguments.of(caseNote(KW_ENTRY_SUBTYPE), AllocationPolicy.KEY_WORKER),
      )
  }
}

private fun RecordedEvent.verifyAgainst(note: CaseNote) {
  assertThat(prisonCode).isEqualTo(note.prisonCode)
  assertThat(staffId).isEqualTo(note.staffId)
  assertThat(username).isEqualTo(note.staffUsername)
  assertThat(personIdentifier).isEqualTo(note.personIdentifier)
  assertThat(occurredAt.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(note.occurredAt.truncatedTo(ChronoUnit.SECONDS))
  assertThat(id).isEqualTo(note.id)
  assertThat(type.code).isEqualTo(
    when (note.type to note.subType) {
      KW_TYPE to KW_SESSION_SUBTYPE -> RecordedEventType.SESSION.name
      KW_TYPE to KW_ENTRY_SUBTYPE -> RecordedEventType.ENTRY.name
      PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE -> RecordedEventType.ENTRY.name
      else -> throw AssertionError("Unexpected case note")
    },
  )
}
