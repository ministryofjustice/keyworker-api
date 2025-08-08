package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PersonalOfficerMigrationInformation
import uk.gov.justice.digital.hmpps.keyworker.migration.Movement
import uk.gov.justice.digital.hmpps.keyworker.migration.PoHistoricAllocation
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class MigratePersonalOfficerHistoryIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .post()
      .uri(INIT_MIGRATION_URL, "NEP")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    webTestClient
      .post()
      .uri(INIT_MIGRATION_URL, "NE1")
      .headers(setHeaders(username = "keyworker-ui", roles = emptyList()))
      .exchange()
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `migration creates migrated personal officer records all prisoners residents`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val prisonCode = "PM1"
    val historicAllocations =
      generateHistoricAllocations(prisonCode, personIdentifier(), 3) +
        generateHistoricAllocations(prisonCode, personIdentifier(), 3)
    prisonMockServer.stubPoAllocationHistory(prisonCode, historicAllocations)
    prisonerSearchMockServer.stubFindAllPrisoners(
      prisonCode,
      prisoners(historicAllocations.map { it.offenderNo }.toSet()),
    )

    publishEventToTopic(migrationEvent(prisonCode))
    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val personIdentifiers = historicAllocations.map { it.offenderNo }.toSet()
    personIdentifiers
      .map {
        allocationRepository.findAllByPersonIdentifier(it).sortedByDescending { a -> a.allocatedAt }
      }.forEach { allocations ->
        assertThat(allocations).hasSize(3)
        allocations.verifyTimeline()
        assertThat(allocations.first().isActive).isTrue()
      }

    staffRoleRepository.findAllByPrisonCodeAndStaffIdIn(prisonCode, historicAllocations.map { it.staffId }.toSet()).also {
      assertThat(it.size).isEqualTo(2)
    }
  }

  @Test
  fun `migration creates deallocated personal officer records for prisoners no longer resident`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val prisonCode = "PM2"
    val transferredPrisoner =
      generateHistoricAllocations(prisonCode, personIdentifier(), 3)
        .groupBy { it.offenderNo }
    val releasedPrisoner =
      generateHistoricAllocations(prisonCode, personIdentifier(), 5)
        .groupBy { it.offenderNo }
    val historicAllocations = (transferredPrisoner.values + releasedPrisoner.values).flatten()
    prisonMockServer.stubPoAllocationHistory(prisonCode, historicAllocations)
    prisonerSearchMockServer.stubFindAllPrisoners(prisonCode, prisoners((1..10).map { personIdentifier() }.toSet()))
    val transferredPi = transferredPrisoner.keys.first()
    val transferredAt = LocalDateTime.now().minusDays(5)
    prisonMockServer.stubGetMovements(
      transferredPi,
      transferredPrisoner.values
        .flatten()
        .maxBy { it.assigned }
        .assigned,
      listOf(
        Movement(
          transferredPi,
          transferredAt.toLocalDate(),
          transferredAt.toLocalTime().plusHours(1),
          prisonCode,
          "AN0",
          "ADM",
          "26",
          "IN",
        ),
        Movement(
          transferredPi,
          transferredAt.toLocalDate(),
          transferredAt.toLocalTime(),
          prisonCode,
          "AN0",
          "TRN",
          "COMP",
          "OUT",
        ),
        Movement(
          transferredPi,
          transferredAt.toLocalDate().minusDays(2),
          transferredAt.toLocalTime().minusHours(1),
          "SOM",
          prisonCode,
          "ADM",
          "25",
          "IN",
        ),
        Movement(
          transferredPi,
          transferredAt.toLocalDate().minusDays(2),
          transferredAt.toLocalTime().minusHours(2),
          "SOM",
          prisonCode,
          "TRN",
          "NOTR",
          "OUT",
        ),
      ),
    )
    val releasedPi = releasedPrisoner.keys.first()
    val releasedAt = LocalDateTime.now().minusDays(3)
    prisonMockServer.stubGetMovements(
      releasedPi,
      releasedPrisoner.values
        .flatten()
        .maxBy { it.assigned }
        .assigned,
      listOf(
        Movement(
          releasedPi,
          releasedAt.toLocalDate(),
          releasedAt.toLocalTime(),
          prisonCode,
          "OUT",
          "REL",
          "CR",
          "OUT",
        ),
      ),
    )

    publishEventToTopic(migrationEvent(prisonCode))
    await untilCallTo { domainEventsQueue.countAllMessagesOnQueue() } matches { it == 0 }

    val transferred =
      allocationRepository.findAllByPersonIdentifier(transferredPi).sortedByDescending { a -> a.allocatedAt }
    assertThat(transferred.size).isEqualTo(3)
    assertThat(transferred.all { !it.isActive }).isTrue()
    transferred.verifyTimeline()
    with(transferred.first()) {
      assertThat(deallocatedBy).isEqualTo(SYSTEM_USERNAME)
      assertThat(deallocationReason?.code).isEqualTo(DeallocationReason.TRANSFER.reasonCode)
      assertThat(deallocatedAt?.truncatedTo(ChronoUnit.SECONDS))
        .isEqualTo(transferredAt?.truncatedTo(ChronoUnit.SECONDS))
    }

    val released =
      allocationRepository.findAllByPersonIdentifier(releasedPi).sortedByDescending { a -> a.allocatedAt }
    assertThat(released.size).isEqualTo(5)
    assertThat(released.all { !it.isActive }).isTrue()
    released.verifyTimeline()
    with(released.first()) {
      assertThat(deallocatedBy).isEqualTo(SYSTEM_USERNAME)
      assertThat(deallocationReason?.code).isEqualTo(DeallocationReason.RELEASED.reasonCode)
      assertThat(deallocatedAt?.truncatedTo(ChronoUnit.SECONDS))
        .isEqualTo(releasedAt?.truncatedTo(ChronoUnit.SECONDS))
    }
  }

  private fun List<Allocation>.verifyTimeline() {
    (1..lastIndex).forEach { index ->
      assertThat(get(index).deallocatedAt?.truncatedTo(ChronoUnit.SECONDS))
        .isEqualTo(get(index - 1).allocatedAt.truncatedTo(ChronoUnit.SECONDS))
      assertThat(get(index).deallocatedBy).isEqualTo(get(index - 1).allocatedBy)
    }
  }

  private fun prisoners(identifiers: Set<String>): Prisoners =
    Prisoners(
      identifiers.map {
        Prisoner(
          it,
          "First",
          "Last",
          now().minusDays(90),
          now().plusDays(180),
          "DEF",
          "Default Prison",
          "DEF-A-1",
          "STANDARD",
          null,
          now().minusDays(30),
          listOf(),
        )
      },
    )

  private fun generateHistoricAllocations(
    prisonCode: String,
    personIdentifier: String,
    number: Int,
  ) = (1..number).map {
    historicAllocation(prisonCode, personIdentifier, LocalDateTime.now().minusWeeks(it.toLong()))
  }

  private fun historicAllocation(
    prisonCode: String,
    personIdentifier: String,
    assigned: LocalDateTime,
    staffId: Long = newId(),
    userId: String = TEST_USERNAME,
    createdAt: LocalDateTime = LocalDateTime.now(),
    createdBy: String = ANOTHER_USERNAME,
  ) = PoHistoricAllocation(prisonCode, personIdentifier, staffId, userId, assigned, createdAt, createdBy)

  private fun migrationEvent(prisonCode: String) =
    HmppsDomainEvent(
      EventType.MigratePersonalOfficers.name,
      PersonalOfficerMigrationInformation(prisonCode),
    )

  companion object {
    const val INIT_MIGRATION_URL = "/prisons/{prisonCode}/personal-officer/migrate"
    const val TEST_USERNAME = "T35TUS3R"
    const val ANOTHER_USERNAME = "An07H3R"
  }
}
