package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonReference
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.utils.IdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.time.LocalDateTime
import java.util.UUID

class MergePrisonerIntTest : IntegrationTest() {
  @Test
  fun `receiving a merge event where no allocation information exists does not cause a failure`() {
    val oldNoms = personIdentifier()
    val newNoms = personIdentifier()

    publishEventToTopic(mergeEvent(newNoms, oldNoms))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }
  }

  @Test
  fun `merge updates prison number for allocation`() {
    val prisonCode = "MNA"
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))
    val newNoms = personIdentifier()
    val caseNote = caseNote(KW_SESSION_SUBTYPE, personIdentifier = alloc.personIdentifier)
//    givenRecordedEvent(
//      caseNote.asRecordedEvent(
//        { type, subtype ->
//          requireNotNull(
//            caseNoteRecordedEventRepository.findByKey(CaseNoteTypeKey(type, subtype))
//          ).type
//        }
//      ))
    caseNotesMockServer.stubGetAllocationCaseNotes(
      newNoms,
      CaseNotes(listOf(caseNote.copy(personIdentifier = newNoms))),
    )

    publishEventToTopic(mergeEvent(newNoms, alloc.personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val merged = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(merged.personIdentifier).isEqualTo(newNoms)

    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(merged, merged.id, RevisionType.MOD, affected, AllocationContext.get())
  }

  @Test
  fun `merge deactivates old allocation if new one exists`() {
    val prisonCode = "MDO"
    val staffId = newId()
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId = staffId))
    val new = givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId = staffId))
    caseNotesMockServer.stubGetAllocationCaseNotes(new.personIdentifier, CaseNotes(listOf()))

    publishEventToTopic(mergeEvent(new.personIdentifier, alloc.personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val merged = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(merged.personIdentifier).isEqualTo(new.personIdentifier)
    assertThat(merged.isActive).isFalse()
    assertThat(merged.deallocationReason?.code).isEqualTo(DeallocationReason.MERGED.reasonCode)
  }

  private fun mergeEvent(
    newNoms: String,
    oldNoms: String,
  ): HmppsDomainEvent<MergeInformation> =
    HmppsDomainEvent(
      EventType.PrisonMerged.name,
      MergeInformation(newNoms, oldNoms),
      PersonReference.withIdentifier(newNoms),
      description = "A prisoner was merged",
    )

  private fun caseNote(
    subType: String,
    type: String = KW_TYPE,
    personIdentifier: String = personIdentifier(),
    occurredAt: LocalDateTime = LocalDateTime.now().minusDays(1),
    staffId: Long = newId(),
    staffUsername: String = username(),
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
}
