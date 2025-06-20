package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.verify
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerEntry
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerInteraction
import uk.gov.justice.digital.hmpps.keyworker.domain.KeyworkerSession
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.asKeyworkerInteraction
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
  fun `creates a new session entry when case note created`(caseNote: CaseNote) {
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteCreated, caseNoteInfo(caseNote), caseNote.personIdentifier)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val interaction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(interaction).isNotNull()
    interaction!!.verifyAgainst(caseNote)
  }

  @ParameterizedTest
  @MethodSource("caseNoteUpdated")
  fun `updates session entry when case note updated`(caseNote: CaseNote) {
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteUpdated, caseNoteInfo(caseNote), caseNote.personIdentifier)
    val existingOccurredAt = LocalDateTime.now().minusDays(4).truncatedTo(ChronoUnit.SECONDS)
    givenKeyworkerInteraction(caseNote.copy(occurredAt = existingOccurredAt).asKeyworkerInteraction()!!)
    val existingInteraction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(existingInteraction).isNotNull()
    assertThat(existingInteraction!!.occurredAt).isEqualTo(existingOccurredAt)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val interaction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(interaction).isNotNull()
    interaction!!.verifyAgainst(caseNote)
  }

  @ParameterizedTest
  @MethodSource("caseNoteMoved")
  fun `moves session entry when case note moved`(caseNote: CaseNote) {
    caseNotesMockServer.stubGetCaseNote(caseNote)
    val event = caseNoteEvent(CaseNoteMoved, caseNoteInfo(caseNote, personIdentifier()), caseNote.personIdentifier)
    givenKeyworkerInteraction(
      caseNote
        .copy(personIdentifier = event.additionalInformation.previousNomsNumber!!)
        .asKeyworkerInteraction()!!,
    )
    val existingInteraction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(existingInteraction).isNotNull()
    assertThat(existingInteraction!!.personIdentifier).isNotEqualTo(caseNote.personIdentifier)
    assertThat(existingInteraction.personIdentifier).isEqualTo(event.additionalInformation.previousNomsNumber)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val interaction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(interaction).isNotNull()
    interaction!!.verifyAgainst(caseNote)
  }

  @ParameterizedTest
  @MethodSource("caseNoteDeleted")
  fun `deletes session entry when case note deleted`(caseNote: CaseNote) {
    val event = caseNoteEvent(CaseNoteDeleted, caseNoteInfo(caseNote), caseNote.personIdentifier)
    givenKeyworkerInteraction(caseNote.asKeyworkerInteraction()!!)
    val existingInteraction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(existingInteraction).isNotNull()
    existingInteraction!!.verifyAgainst(caseNote)

    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val interaction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(interaction).isNull()
  }

  @ParameterizedTest
  @MethodSource("caseNoteTypeChanged")
  fun `deletes session entry when case note changed to non keyworker type`(caseNote: CaseNote) {
    val updateCaseNote = caseNote.copy(type = "ANY", subType = "OTHER")
    caseNotesMockServer.stubGetCaseNote(updateCaseNote)
    val event =
      caseNoteEvent(
        CaseNoteUpdated,
        caseNoteInfo(updateCaseNote),
        caseNote.personIdentifier,
      )
    givenKeyworkerInteraction(caseNote.asKeyworkerInteraction()!!)
    val existingInteraction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(existingInteraction).isNotNull()
    existingInteraction!!.verifyAgainst(caseNote)

    publishEventToTopic(event, updateCaseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val interaction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(interaction).isNull()
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
    givenKeyworkerInteraction(caseNote.asKeyworkerInteraction()!!)
    val existingInteraction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(existingInteraction).isNotNull()
    existingInteraction!!.verifyAgainst(caseNote)

    publishEventToTopic(event, updateCaseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val newInteraction = interactionRepository(updateCaseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(newInteraction).isNotNull()

    val deletedInteraction = interactionRepository(caseNote.subType).findByIdOrNull(caseNote.id)
    assertThat(deletedInteraction).isNull()
  }

  @Test
  fun `non keyworker case notes are ignored`() {
    val caseNote = caseNote("OTHER", "ANY")
    val event = caseNoteEvent(CaseNoteCreated, caseNoteInfo(caseNote), caseNote.personIdentifier)
    publishEventToTopic(event, caseNote.snsAttributes())

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    verify(telemetryClient).trackEvent(
      "CaseNoteNotOfInterest",
      mapOf("name" to CaseNoteCreated.name, "type" to "ANY", "subType" to "OTHER"),
      null,
    )
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

  private fun interactionRepository(subType: String): JpaRepository<out KeyworkerInteraction, UUID> =
    when (subType) {
      KW_SESSION_SUBTYPE -> ksRepository
      KW_ENTRY_SUBTYPE -> keRepository
      else -> throw IllegalArgumentException("Unknown case note sub type")
    }

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
      text: String = "Some text entered about the interaction",
      amendments: List<CaseNoteAmendment> = listOf(CaseNoteAmendment("Amended text")),
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
