package uk.gov.justice.digital.hmpps.keyworker.integration

import io.jsonwebtoken.security.Jwks.OP.policy
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.events.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.PrisonStatisticsInfo
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.MANUAL
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL
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
    val noteUsageResponse =
      if (policy == AllocationPolicy.KEY_WORKER) {
        kwCaseNoteResponse(prisoners.personIdentifiers())
      } else {
        poCaseNoteResponse(prisoners.personIdentifiers())
      }
    caseNotesMockServer.stubUsageByPersonIdentifier(
      if (policy == AllocationPolicy.KEY_WORKER) {
        UsageByPersonIdentifierRequest.Companion.keyworkerTypes(
          prisonCode,
          prisoners.personIdentifiers(),
          yesterday.atStartOfDay(),
        )
      } else {
        UsageByPersonIdentifierRequest.Companion.personalOfficerTypes(
          prisonCode,
          prisoners.personIdentifiers(),
          yesterday.atStartOfDay(),
        )
      },
      noteUsageResponse,
    )
    val peopleWithSessions = CaseNoteSummary(noteUsageResponse.content).personIdentifiersWithSessions()
    if (policy == AllocationPolicy.KEY_WORKER) {
      caseNotesMockServer.stubUsageByPersonIdentifier(
        UsageByPersonIdentifierRequest.Companion.sessionTypes(
          prisonCode,
          peopleWithSessions,
          yesterday.minusMonths(6),
          yesterday.minusDays(1),
        ),
        previousSessionsResponse(peopleWithSessions),
      )
    } else {
      caseNotesMockServer.stubUsageByPersonIdentifier(
        UsageByPersonIdentifierRequest.Companion.personalOfficerTypes(
          prisonCode,
          peopleWithSessions,
          yesterday.minusMonths(6).atStartOfDay(),
          yesterday.minusDays(1).atStartOfDay(),
        ),
        previousSessionsResponse(peopleWithSessions),
      )
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
      assertThat(stats.recordedSessionCount).isEqualTo(40)
      assertThat(stats.recordedEntryCount).isEqualTo(9)
      assertThat(stats.receptionToAllocationDays).isEqualTo(30)
      assertThat(stats.receptionToSessionDays).isEqualTo(24)
    } else {
      assertThat(stats.recordedSessionCount).isEqualTo(40)
      assertThat(stats.recordedEntryCount).isEqualTo(0)
      assertThat(stats.receptionToAllocationDays).isEqualTo(30)
      assertThat(stats.receptionToSessionDays).isEqualTo(24)
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
    val noteUsageResponse = kwCaseNoteResponse(eligiblePrisoners)
    caseNotesMockServer.stubUsageByPersonIdentifier(
      UsageByPersonIdentifierRequest.Companion.keyworkerTypes(prisonCode, eligiblePrisoners, yesterday.atStartOfDay()),
      noteUsageResponse,
    )
    val peopleWithSessions = CaseNoteSummary(noteUsageResponse.content).personIdentifiersWithSessions()
    caseNotesMockServer.stubUsageByPersonIdentifier(
      UsageByPersonIdentifierRequest.Companion.sessionTypes(
        prisonCode,
        peopleWithSessions,
        yesterday.minusMonths(6),
        yesterday.minusDays(1),
      ),
      previousSessionsResponse(peopleWithSessions),
    )

    publishEventToTopic(calculateStatsEvent(PrisonStatisticsInfo(prisonCode, now().minusDays(1), AllocationPolicy.KEY_WORKER)))

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

    assertThat(stats.recordedSessionCount).isEqualTo(32)
    assertThat(stats.recordedEntryCount).isEqualTo(7)

    assertThat(stats.receptionToAllocationDays).isEqualTo(55)
    assertThat(stats.receptionToSessionDays).isEqualTo(37)
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

  private fun poCaseNoteResponse(personIdentifiers: Set<String>): NoteUsageResponse<UsageByPersonIdentifierResponse> =
    NoteUsageResponse(
      personIdentifiers
        .mapIndexed { index, pi ->
          buildSet {
            if (index % 3 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  PO_ENTRY_TYPE,
                  PO_ENTRY_SUBTYPE,
                  if (index % 18 == 0) 2 else 1,
                  LatestNote(LocalDateTime.now().minusDays(1)),
                ),
              )
            }
          }
        }.flatten()
        .groupBy { it.personIdentifier },
    )

  private fun kwCaseNoteResponse(personIdentifiers: Set<String>): NoteUsageResponse<UsageByPersonIdentifierResponse> =
    NoteUsageResponse(
      personIdentifiers
        .mapIndexed { index, pi ->
          buildSet {
            if (index % 3 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  KW_TYPE,
                  KW_SESSION_SUBTYPE,
                  if (index % 18 == 0) 2 else 1,
                  LatestNote(LocalDateTime.now().minusDays(1)),
                ),
              )
            }
            if (index % 12 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  KW_TYPE,
                  KW_ENTRY_SUBTYPE,
                  1,
                  LatestNote(LocalDateTime.now().minusDays(1)),
                ),
              )
            }
          }
        }.flatten()
        .groupBy { it.personIdentifier },
    )

  private fun previousSessionsResponse(personIdentifiers: Set<String>): NoteUsageResponse<UsageByPersonIdentifierResponse> {
    val (type, subtype) =
      if (AllocationContext.get().policy == AllocationPolicy.KEY_WORKER) {
        KW_TYPE to KW_SESSION_SUBTYPE
      } else {
        PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE
      }
    return NoteUsageResponse(
      personIdentifiers
        .mapIndexed { index, pi ->
          buildSet {
            if (index % 4 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  type,
                  subtype,
                  3 * index / personIdentifiers.size,
                  LatestNote(LocalDateTime.now().minusDays(7)),
                ),
              )
            }
          }
        }.flatten()
        .groupBy { it.personIdentifier },
    )
  }
}

private fun calculateStatsEvent(
  info: PrisonStatisticsInfo,
  type: EventType = EventType.CalculatePrisonStats,
): HmppsDomainEvent<PrisonStatisticsInfo> = HmppsDomainEvent(type.name, info)
