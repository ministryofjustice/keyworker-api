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
import uk.gov.justice.digital.hmpps.keyworker.integration.complexityofneed.ComplexityOfNeed
import uk.gov.justice.digital.hmpps.keyworker.integration.events.offender.ComplexityOfNeedChange
import uk.gov.justice.digital.hmpps.keyworker.integration.events.offender.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ComplexityOfNeedChangedIntTest : IntegrationTest() {
  @Test
  fun `receiving a change event that is not active is ignored without failing`() {
    val prisonCode = "CII"
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))
    publishEventToTopic(complexityChanged(ComplexityOfNeedLevel.HIGH, alloc.personIdentifier, false))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val allocation = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(allocation.deallocationReason).isNull()
    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(allocation, allocation.id, RevisionType.ADD, affected, AllocationContext.get())
  }

  @Test
  fun `receiving a change event that is not a high does not deallocate`() {
    val prisonCode = "CMI"
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))
    publishEventToTopic(complexityChanged(ComplexityOfNeedLevel.MEDIUM, alloc.personIdentifier, true))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val allocation = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(allocation.deallocationReason).isNull()
    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(allocation, allocation.id, RevisionType.ADD, affected, AllocationContext.get())
  }

  @Test
  fun `changing to high complexity of need deallocates keyworker allocations`() {
    val prisonCode = "CHD"
    val username = "H1ghC0"
    val requestedAt = LocalDateTime.now().minusMinutes(30)
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))

    complexityOfNeedMockServer.stubComplexOffenders(
      setOf(alloc.personIdentifier),
      listOf(
        ComplexityOfNeed(
          alloc.personIdentifier,
          ComplexityOfNeedLevel.HIGH,
          sourceUser = username,
          updatedTimeStamp = requestedAt,
        ),
      ),
    )

    publishEventToTopic(complexityChanged(ComplexityOfNeedLevel.HIGH, alloc.personIdentifier, true))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val deallocated = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(deallocated.deallocationReason?.code).isEqualTo(DeallocationReason.CHANGE_IN_COMPLEXITY_OF_NEED.name)
    assertThat(deallocated.deallocatedAt?.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(requestedAt?.truncatedTo(ChronoUnit.SECONDS))
    assertThat(deallocated.deallocatedBy).isEqualTo(username)
    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(
      deallocated,
      deallocated.id,
      RevisionType.MOD,
      affected,
      AllocationContext.get().copy(username = username, requestAt = requestedAt),
    )
  }

  @Test
  fun `changing to high complexity of need deallocates personal officer allocations`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val prisonCode = "CHD"
    val username = "H1ghC0"
    val requestedAt = LocalDateTime.now().minusMinutes(30)
    val alloc = givenAllocation(staffAllocation(personIdentifier(), prisonCode))

    complexityOfNeedMockServer.stubComplexOffenders(
      setOf(alloc.personIdentifier),
      listOf(
        ComplexityOfNeed(
          alloc.personIdentifier,
          ComplexityOfNeedLevel.HIGH,
          sourceUser = username,
          updatedTimeStamp = requestedAt,
        ),
      ),
    )

    publishEventToTopic(complexityChanged(ComplexityOfNeedLevel.HIGH, alloc.personIdentifier, true))

    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val deallocated = requireNotNull(allocationRepository.findByIdOrNull(alloc.id))
    assertThat(deallocated.deallocationReason?.code).isEqualTo(DeallocationReason.CHANGE_IN_COMPLEXITY_OF_NEED.name)
    assertThat(deallocated.deallocatedAt?.truncatedTo(ChronoUnit.SECONDS)).isEqualTo(requestedAt?.truncatedTo(ChronoUnit.SECONDS))
    assertThat(deallocated.deallocatedBy).isEqualTo(username)
    val affected = setOf(Allocation::class.simpleName!!)
    verifyAudit(
      deallocated,
      deallocated.id,
      RevisionType.MOD,
      affected,
      AllocationContext.get().copy(username = username, requestAt = requestedAt),
    )
  }

  private fun complexityChanged(
    level: ComplexityOfNeedLevel,
    personIdentifier: String = personIdentifier(),
    active: Boolean? = true,
  ): ComplexityOfNeedChange = ComplexityOfNeedChange(personIdentifier, level.name.lowercase(), active)
}
