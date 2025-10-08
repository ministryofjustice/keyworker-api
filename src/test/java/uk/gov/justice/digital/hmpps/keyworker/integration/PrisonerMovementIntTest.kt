package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.dto.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.events.OffenderEvent
import uk.gov.justice.digital.hmpps.keyworker.events.OffenderEventListener.Companion.EXTERNAL_MOVEMENT
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier

class PrisonerMovementIntTest : IntegrationTest() {
  @Test
  fun `receiving a movement event where no allocation information exists does not cause a failure`() {
    val prisonCode = "POR"
    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, prisonCode)))
    publishOffenderEvent(EXTERNAL_MOVEMENT, offenderEvent("NIM", prisonCode, "OUT", "TRN"))

    await untilCallTo { offenderEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }
  }

  @Test
  fun `release deallocates allocation`() {
    val prisonCode = "DAA"
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))

    publishOffenderEvent(EXTERNAL_MOVEMENT, offenderEvent(prisonCode, "OUT", "OUT", "REL", alloc.personIdentifier))

    await untilCallTo { offenderEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val deallocated = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(deallocated.deallocationReason?.code).isEqualTo(DeallocationReason.RELEASED.name)
    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(deallocated, deallocated.id, RevisionType.MOD, affected, AllocationContext.get())
  }

  @Test
  fun `transfer deallocates allocation`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val prisonCode = "SMO"
    val toPrisonCode = "TOP"
    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(toPrisonCode, toPrisonCode)))
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))

    publishOffenderEvent(EXTERNAL_MOVEMENT, offenderEvent(prisonCode, toPrisonCode, "OUT", "TRN", alloc.personIdentifier))

    await untilCallTo { offenderEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val deallocated = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(deallocated.deallocationReason?.code).isEqualTo(DeallocationReason.TRANSFER.name)
    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(deallocated, deallocated.id, RevisionType.MOD, affected, AllocationContext.get())
  }

  @Test
  fun `does not deallocate if already at new prison`() {
    val prisonCode = "ARD"
    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, prisonCode)))
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))

    publishOffenderEvent(EXTERNAL_MOVEMENT, offenderEvent("OTHER", prisonCode, "IN", "ADM", alloc.personIdentifier))

    await untilCallTo { offenderEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val allocation = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(allocation.deallocationReason).isNull()
    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(allocation, allocation.id, RevisionType.ADD, affected, AllocationContext.get())
  }

  private fun offenderEvent(
    fromPrisonCode: String,
    toPrisonCode: String,
    direction: String,
    movementType: String,
    personIdentifier: String = personIdentifier(),
  ): OffenderEvent =
    OffenderEvent(
      fromAgencyLocationId = fromPrisonCode,
      toAgencyLocationId = toPrisonCode,
      offenderIdDisplay = personIdentifier,
      movementType = movementType,
      directionCode = direction,
    )
}
