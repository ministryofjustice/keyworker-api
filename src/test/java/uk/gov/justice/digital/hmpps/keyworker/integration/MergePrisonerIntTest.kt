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
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.prisonNumber

class MergePrisonerIntTest : IntegrationTest() {
  @Test
  fun `receiving a merge event where no keyworker information exists does not cause a failure`() {
    val oldNoms = prisonNumber()
    val newNoms = prisonNumber()

    publishEventToTopic(mergeEvent(newNoms, oldNoms))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }
  }

  @Test
  fun `merge updates prison number for offender keyworker`() {
    val okw = givenOffenderKeyWorker()
    val newNoms = prisonNumber()

    publishEventToTopic(mergeEvent(newNoms, okw.offenderNo))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val merged = requireNotNull(offenderKeyworkerRepository.findByIdOrNull(okw.offenderKeyworkerId))
    assertThat(merged.offenderNo).isEqualTo(newNoms)
  }

  @Test
  fun `merge deactivates old offender keyworker if new one exists`() {
    val staffId = newId()
    val okw = givenOffenderKeyWorker(staffId = staffId)
    val nkw = givenOffenderKeyWorker(staffId = staffId)

    publishEventToTopic(mergeEvent(nkw.offenderNo, okw.offenderNo))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val merged = requireNotNull(offenderKeyworkerRepository.findByIdOrNull(okw.offenderKeyworkerId))
    assertThat(merged.offenderNo).isEqualTo(nkw.offenderNo)
    assertThat(merged.isActive).isFalse()
    assertThat(merged.deallocationReason).isEqualTo(DeallocationReason.MERGED)
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
