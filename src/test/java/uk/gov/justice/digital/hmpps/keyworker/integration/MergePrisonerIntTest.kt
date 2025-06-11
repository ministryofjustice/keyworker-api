package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonReference
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier

class MergePrisonerIntTest : IntegrationTest() {
  @Test
  fun `receiving a merge event where no keyworker information exists does not cause a failure`() {
    val oldNoms = personIdentifier()
    val newNoms = personIdentifier()

    publishEventToTopic(mergeEvent(newNoms, oldNoms))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }
  }

  @Test
  fun `merge updates prison number for offender keyworker`() {
    val okw = givenOffenderKeyWorker()
    val newNoms = personIdentifier()

    publishEventToTopic(mergeEvent(newNoms, okw.personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val merged = requireNotNull(offenderKeyworkerRepository.findByIdOrNull(okw.id))
    assertThat(merged.personIdentifier).isEqualTo(newNoms)
  }

  @Test
  fun `merge deactivates old offender keyworker if new one exists`() {
    val staffId = newId()
    val okw = givenOffenderKeyWorker(staffId = staffId)
    val nkw = givenOffenderKeyWorker(staffId = staffId)

    publishEventToTopic(mergeEvent(nkw.personIdentifier, okw.personIdentifier))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val merged = requireNotNull(offenderKeyworkerRepository.findByIdOrNull(okw.id))
    assertThat(merged.personIdentifier).isEqualTo(nkw.personIdentifier)
    assertThat(merged.isActive).isFalse()
    assertThat(merged.deallocationReason.code).isEqualTo(DeallocationReason.MERGED.reasonCode)
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
