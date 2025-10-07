package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationType.MANUAL
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationType.PROVISIONAL
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.ACTIVE
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus.INACTIVE
import uk.gov.justice.digital.hmpps.keyworker.services.ComplexOffender
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.staffRoles
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
      prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(staff.map { it.staffId }))
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
            allocationType = if (index % 25 == 0) PROVISIONAL else MANUAL,
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

    publishEventToTopic(calculateStatsEvent(PrisonStatisticsInfo(prisonCode, now().minusDays(1), policy)))

    val stats: PrisonStatistic? =
      await untilCallTo {
        setContext(AllocationContext.get().copy(policy = policy))
        prisonStatisticRepository.findByPrisonCodeAndDate(prisonCode, yesterday)
      } matches { it != null }

    assertThat(stats).isNotNull
    assertThat(stats!!.prisonCode).isEqualTo(prisonCode)
    assertThat(stats.date).isEqualTo(yesterday)

    assertThat(stats.prisonerCount).isEqualTo(prisoners.size)
    assertThat(stats.eligiblePrisonerCount).isEqualTo(prisoners.size)

    assertThat(stats.prisonersAssignedCount).isEqualTo(32)
    assertThat(stats.eligibleStaffCount).isEqualTo(6)

    if (policy == AllocationPolicy.KEY_WORKER) {
      assertThat(stats.recordedSessionCount).isEqualTo(12)
      assertThat(stats.recordedEntryCount).isEqualTo(4)
      assertThat(stats.receptionToAllocationDays).isEqualTo(30)
      assertThat(stats.receptionToRecordedEventDays).isEqualTo(4)
    } else {
      assertThat(stats.recordedSessionCount).isEqualTo(0)
      assertThat(stats.recordedEntryCount).isEqualTo(12)
      assertThat(stats.receptionToAllocationDays).isEqualTo(30)
      assertThat(stats.receptionToRecordedEventDays).isEqualTo(4)
    }
  }

  @Test
  fun `calculate prison statistics for yesterday for a prison with complex needs`() {
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
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(keyworkers.map { it.staffId }))
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
            allocationType = if (index % 25 == 0) PROVISIONAL else MANUAL,
          ),
        )
      }
    }

    complexityOfNeedMockServer.stubComplexOffenders(
      prisoners.personIdentifiers(),
      prisoners.content.mapIndexed { index, prisoner ->
        ComplexOffender(
          prisoner.prisonerNumber,
          prisoner.complexityOfNeedLevel ?: ComplexityOfNeedLevel.LOW,
          createdTimeStamp = LocalDateTime.now().minusDays(index.toLong() + 7),
          updatedTimeStamp = LocalDateTime.now().minusDays(index.toLong()),
        )
      },
    )
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
        PrisonStatisticsInfo(
          prisonCode,
          now().minusDays(1),
          AllocationPolicy.KEY_WORKER,
        ),
      ),
    )

    val stats: PrisonStatistic? =
      await untilCallTo {
        prisonStatisticRepository.findByPrisonCodeAndDate(prisonCode, yesterday)
      } matches { it != null }

    assertThat(stats).isNotNull
    assertThat(stats!!.prisonCode).isEqualTo(prisonCode)
    assertThat(stats.date).isEqualTo(yesterday)

    assertThat(stats.prisonerCount).isEqualTo(prisoners.size)
    assertThat(stats.eligiblePrisonerCount).isEqualTo(eligiblePrisoners.size)

    assertThat(stats.prisonersAssignedCount).isEqualTo(25)
    assertThat(stats.eligibleStaffCount).isEqualTo(6)

    assertThat(stats.recordedSessionCount).isEqualTo(9)
    assertThat(stats.recordedEntryCount).isEqualTo(4)

    assertThat(stats.receptionToAllocationDays).isEqualTo(55)
    assertThat(stats.receptionToRecordedEventDays).isEqualTo(34)
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
