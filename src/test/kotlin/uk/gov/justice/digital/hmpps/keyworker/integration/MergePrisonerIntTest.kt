package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNotes
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.PersonReference
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier

class MergePrisonerIntTest : IntegrationTest() {
  @Test
  fun `receiving a merge event where no allocation information exists does not cause a failure`() {
    val oldNoms = personIdentifier()
    val newNoms = personIdentifier()

    publishEventToTopic(mergeEvent(newNoms, oldNoms))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }
  }

  @ParameterizedTest
  @EnumSource(AllocationPolicy::class)
  fun `merge updates prison number for allocation`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "MNA"
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))
    val newNoms = personIdentifier()

    publishEventToTopic(mergeEvent(newNoms, alloc.personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    setContext(AllocationContext.get().copy(policy = policy))
    val merged = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(merged.personIdentifier).isEqualTo(newNoms)

    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(merged, merged.id, RevisionType.MOD, affected, AllocationContext.get())
  }

  @ParameterizedTest
  @EnumSource(AllocationPolicy::class)
  fun `merge deactivates old allocation if new one exists`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "MDO"
    val staffId = newId()
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId = staffId))
    val new = givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId = staffId))
    caseNotesMockServer.stubSearchCaseNotes(new.personIdentifier, CaseNotes(listOf()))

    publishEventToTopic(mergeEvent(new.personIdentifier, alloc.personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    setContext(AllocationContext.get().copy(policy = policy))
    val merged = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(merged.personIdentifier).isEqualTo(new.personIdentifier)
    assertThat(merged.isActive).isFalse()
    assertThat(merged.deallocationReason?.code).isEqualTo(DeallocationReason.MERGED.name)
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
}
