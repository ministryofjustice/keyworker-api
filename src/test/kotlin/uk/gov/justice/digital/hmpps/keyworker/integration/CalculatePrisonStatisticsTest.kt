package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.integration.complexityofneed.ComplexityOfNeed
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.integration.events.offender.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoners
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus.ACTIVE
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus.INACTIVE
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.nomisStaffRoles
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.LocalTime

class CalculatePrisonStatisticsTest : IntegrationTest() {
  @ParameterizedTest
  @EnumSource(value = AllocationPolicy::class)
  fun `calculate prison statistics for yesterday for a prison without complex needs`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "CALWOC"
    val yesterday = now().minusDays(1)
    givenPrisonConfig(prisonConfig(prisonCode, true))
    val staff =
      (0..10).map { index ->
        givenStaffConfig(
          staffConfig(
            if (index % 2 == 0) ACTIVE else INACTIVE,
            NomisIdGenerator.newId(),
          ),
        )
      }
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, nomisStaffRoles(staff.map { it.staffId }))
    } else {
      staff.forEach { givenStaffRole(staffRole(prisonCode, it.staffId)) }
    }
    val additionalStaff =
      (0..5).map { index ->
        givenStaffConfig(
          staffConfig(
            if (index % 2 == 0) ACTIVE else INACTIVE,
            NomisIdGenerator.newId(),
          ),
        )
      }
    val prisoners = prisoners()
    prisonerSearchMockServer.stubFindAllPrisoners(prisonCode, prisoners)
    prisoners.personIdentifiers().forEachIndexed { index, pi ->
      if (index % 3 == 0) {
        givenAllocation(
          staffAllocation(
            pi,
            prisonCode,
            (staff + additionalStaff).filter { it.status.code == ACTIVE.name }.random().staffId,
            yesterday.minusDays(index % 10L).atTime(LocalTime.now()),
          ),
        )
      }
    }
    val recordedEventType =
      when (policy) {
        AllocationPolicy.KEY_WORKER -> RecordedEventType.SESSION
        else -> RecordedEventType.ENTRY
      }
    val recordedEvents =
      prisoners.content.mapIndexedNotNull { idx, prisoner ->
        if (idx % 9 == 0) {
          givenRecordedEvent(
            recordedEvent(
              prisonCode,
              recordedEventType,
              yesterday.atTime(LocalTime.now()),
              personIdentifier = prisoner.prisonerNumber,
            ),
          )
        } else {
          if (policy == AllocationPolicy.KEY_WORKER && idx % 15 == 0) {
            givenRecordedEvent(
              recordedEvent(
                prisonCode,
                RecordedEventType.ENTRY,
                yesterday.atTime(LocalTime.now()),
                personIdentifier = prisoner.prisonerNumber,
              ),
            )
          }
          null
        }
      }

    recordedEvents.map { it.personIdentifier }.forEachIndexed { index, personIdentifier ->
      if (index % 2 == 0) {
        givenRecordedEvent(
          recordedEvent(
            prisonCode,
            recordedEventType,
            yesterday.minusDays(10).atTime(LocalTime.now()),
            personIdentifier = personIdentifier,
          ),
        )
      }
    }

    publishEventToTopic(calculateStatsEvent(PrisonStatisticsInfo(prisonCode, yesterday, policy)))

    setContext(AllocationContext.get().copy(policy = policy))
    var stats: PrisonStatistic?
    do {
      stats = prisonStatisticRepository.findByPrisonCodeAndDate(prisonCode, yesterday)
    } while (stats == null)

    assertThat(stats).isNotNull
    assertThat(stats.prisonCode).isEqualTo(prisonCode)
    assertThat(stats.date).isEqualTo(yesterday)

    assertThat(stats.prisonerCount).isEqualTo(prisoners.size)
    assertThat(stats.eligiblePrisonerCount).isEqualTo(prisoners.size)

    assertThat(stats.prisonersAssignedCount).isEqualTo(34)
    assertThat(stats.eligibleStaffCount).isEqualTo(6)

    assertThat(stats.receptionToAllocationDays).isEqualTo(22)
    assertThat(stats.receptionToRecordedEventDays).isEqualTo(4)

    val prisonerStats = prisonerStatisticRepository.findAll().filter { it.prisonStatistic.id == stats.id }
    assertThat(prisonerStats).hasSize(prisoners.size)
    prisonerStats.forEach {
      assertThat(it.allocationEligibilityDate).isEqualTo(prisoners[it.personIdentifier]?.lastAdmissionDate)
    }
  }

  @Test
  fun `calculate prison statistics for yesterday for a prison with complex needs`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    val prisonCode = "CALWIC"
    val yesterday = now().minusDays(1)
    givenPrisonConfig(prisonConfig(prisonCode, true, hasPrisonersWithHighComplexityNeeds = true))
    val keyworkers =
      (0..10).map { index ->
        givenStaffConfig(
          staffConfig(
            if (index % 2 == 0) ACTIVE else INACTIVE,
            NomisIdGenerator.newId(),
          ),
        )
      }
    prisonMockServer.stubKeyworkerSearch(prisonCode, nomisStaffRoles(keyworkers.map { it.staffId }))
    val prisoners = prisoners(includeComplexNeeds = true)
    prisonerSearchMockServer.stubFindAllPrisoners(prisonCode, prisoners)
    val eligiblePrisoners =
      prisoners.content
        .filter { it.complexityOfNeedLevel != ComplexityOfNeedLevel.HIGH }
        .map { it.prisonerNumber }
        .toSet()
    eligiblePrisoners.forEachIndexed { index, pi ->
      if (index % 3 == 0) {
        givenAllocation(
          staffAllocation(
            pi,
            prisonCode,
            keyworkers.filter { it.status.code == ACTIVE.name }.random().staffId,
            yesterday.minusDays(index % 10L).atTime(LocalTime.now()),
          ),
        )
      }
    }

    val complexityOfNeed =
      prisoners.content
        .mapIndexed { index, prisoner ->
          ComplexityOfNeed(
            prisoner.prisonerNumber,
            prisoner.complexityOfNeedLevel ?: ComplexityOfNeedLevel.LOW,
            createdTimeStamp = LocalDateTime.now().minusDays(index.toLong() + 7),
            updatedTimeStamp = LocalDateTime.now().minusDays(index.toLong()),
          )
        }.associateBy { it.personIdentifier }
    complexityOfNeedMockServer.stubComplexOffenders(prisoners.personIdentifiers(), complexityOfNeed.values)
    eligiblePrisoners.mapIndexedNotNull { idx, pi ->
      if (idx % 9 == 0) {
        givenRecordedEvent(
          recordedEvent(
            prisonCode,
            RecordedEventType.SESSION,
            yesterday.atTime(LocalTime.now()),
            personIdentifier = pi,
          ),
        )
      } else {
        if (idx % 15 == 0) {
          givenRecordedEvent(
            recordedEvent(
              prisonCode,
              RecordedEventType.ENTRY,
              yesterday.atTime(LocalTime.now()),
              personIdentifier = pi,
            ),
          )
        }
        null
      }
    }

    publishEventToTopic(
      calculateStatsEvent(
        PrisonStatisticsInfo(prisonCode, yesterday, AllocationPolicy.KEY_WORKER),
      ),
    )

    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    var stats: PrisonStatistic?
    do {
      stats = prisonStatisticRepository.findByPrisonCodeAndDate(prisonCode, yesterday)
    } while (stats == null)

    assertThat(stats).isNotNull
    assertThat(stats.prisonCode).isEqualTo(prisonCode)
    assertThat(stats.date).isEqualTo(yesterday)

    assertThat(stats.prisonerCount).isEqualTo(prisoners.size)
    assertThat(stats.eligiblePrisonerCount).isEqualTo(eligiblePrisoners.size)

    assertThat(stats.prisonersAssignedCount).isEqualTo(27)
    assertThat(stats.eligibleStaffCount).isEqualTo(6)

    assertThat(stats.receptionToAllocationDays).isEqualTo(55)
    assertThat(stats.receptionToRecordedEventDays).isEqualTo(34)

    val prisonerStats = prisonerStatisticRepository.findAll().filter { it.prisonStatistic.id == stats.id }
    assertThat(prisonerStats).hasSize(prisoners.size)
    prisonerStats.forEach {
      val prisoner = prisoners[it.personIdentifier]
      val con = complexityOfNeed[it.personIdentifier]
      if (prisoner?.complexityOfNeedLevel == ComplexityOfNeedLevel.HIGH) {
        assertThat(it.allocationEligibilityDate).isNull()
      } else {
        assertThat(it.allocationEligibilityDate).isEqualTo(
          listOfNotNull(
            prisoner?.lastAdmissionDate,
            con?.updatedTimeStamp?.toLocalDate(),
          ).maxOrNull(),
        )
      }
    }
  }

  private fun prisoners(
    count: Int = 100,
    includeComplexNeeds: Boolean = false,
  ): Prisoners {
    val nonHigh = listOf(ComplexityOfNeedLevel.LOW, ComplexityOfNeedLevel.MEDIUM, null)
    return Prisoners(
      (1..count).map { index ->
        Prisoner(
          NomisIdGenerator.personIdentifier(),
          "First",
          "Last",
          now().minusDays(index / 2 + 1L),
          now().plusDays(index * 2 + 2L),
          "DEF",
          "DEF",
          "Default Prison",
          "DEF-A-1",
          "STANDARD",
          if (!includeComplexNeeds) {
            null
          } else if (index % 5 == 0) {
            ComplexityOfNeedLevel.HIGH
          } else {
            nonHigh.random()
          },
          if (index % 2 == 0) null else now().minusDays(index / 2 + 1L),
          listOf(),
        )
      },
    )
  }
}

private fun calculateStatsEvent(
  info: PrisonStatisticsInfo,
  type: EventType = EventType.CalculatePrisonStats,
): HmppsDomainEvent<PrisonStatisticsInfo> = HmppsDomainEvent(type.name, info)
