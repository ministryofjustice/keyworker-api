package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
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
    initMigration("NE1", null).expectStatus().isForbidden
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

    initMigration(prisonCode)

    val personIdentifiers = historicAllocations.map { it.offenderNo }.toSet()
    Thread.sleep(1000) // TODO look into alternative

    personIdentifiers
      .map {
        allocationRepository.findAllByPersonIdentifier(it).sortedByDescending { a -> a.allocatedAt }
      }.forEach { allocations ->
        assertThat(allocations).hasSize(3)
        allocations.verifyTimeline()
        assertThat(allocations.first().isActive).isTrue()
      }

    staffRoleRepository
      .findAllByPrisonCodeAndStaffIdIn(prisonCode, historicAllocations.map { it.staffId }.toSet())
      .also {
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
    val releasedPi = releasedPrisoner.keys.first()
    val releasedAt = LocalDateTime.now().minusDays(3)
    prisonMockServer.stubGetMovements(
      transferredPi,
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
          transferredAt.plusHours(1),
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
          transferredAt,
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
          transferredAt.minusHours(1),
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
          transferredAt.minusHours(2),
        ),
      ),
    )
    prisonMockServer.stubGetMovements(
      releasedPi,
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
          releasedAt,
        ),
      ),
    )

    initMigration(prisonCode)
    Thread.sleep(1000) // TODO look into alternative

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

  private fun initMigration(
    prisonCode: String,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .post()
    .uri(INIT_MIGRATION_URL, prisonCode)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val INIT_MIGRATION_URL = "/prisons/{prisonCode}/personal-officer/migrate"
    const val TEST_USERNAME = "T35TUS3R"
    const val ANOTHER_USERNAME = "An07H3R"
  }
}
