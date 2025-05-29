package uk.gov.justice.digital.hmpps.keyworker.statistics

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonStatistic
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.Prisoners
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.TRANSFER_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNoteSummary
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.LatestNote
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.NoteUsageResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.keyworkerTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.sessionTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierRequest.Companion.transferTypes
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.UsageByPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.MANUAL
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationType.PROVISIONAL
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus.ACTIVE
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus.INACTIVE
import uk.gov.justice.digital.hmpps.keyworker.services.ComplexOffender
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate.now
import java.time.LocalDateTime
import java.time.LocalTime

class CalculatePrisonStatisticsTest : IntegrationTest() {
  @Test
  fun `calculate prison statistics for yesterday for a prison without complex needs`() {
    val prisonCode = "CALWOC"
    val yesterday = now().minusDays(1)
    givenPrisonConfig(prisonConfig(prisonCode, true))
    val keyworkers =
      (0..10).map { index -> givenKeyworkerConfig(keyworkerConfig(if (index % 2 == 0) ACTIVE else INACTIVE, newId())) }
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(keyworkers.map { it.staffId }))
    val additionalKeyworkers =
      (0..5).map { index -> givenKeyworkerConfig(keyworkerConfig(if (index % 2 == 0) ACTIVE else INACTIVE, newId())) }
    val prisoners = prisoners()
    prisonerSearchMockServer.stubFindAllPrisoners(prisonCode, prisoners)
    prisoners.personIdentifiers().forEachIndexed { index, pi ->
      if (index % 3 == 0) {
        givenKeyworkerAllocation(
          keyworkerAllocation(
            pi,
            prisonCode,
            (keyworkers + additionalKeyworkers).filter { it.status.code == ACTIVE.name }.random().staffId,
            yesterday.minusDays(index % 10L).atTime(LocalTime.now()),
            allocationType = if (index % 25 == 0) PROVISIONAL else MANUAL,
          ),
        )
      }
    }
    val noteUsageResponse = noteUsageResponse(prisoners.personIdentifiers())
    caseNotesMockServer.stubUsageByPersonIdentifier(
      keyworkerTypes(prisonCode, prisoners.personIdentifiers(), yesterday),
      noteUsageResponse,
    )
    val peopleWithSessions = CaseNoteSummary(noteUsageResponse.content).personIdentifiersWithSessions()
    caseNotesMockServer.stubUsageByPersonIdentifier(
      sessionTypes(prisonCode, peopleWithSessions, yesterday.minusMonths(6), yesterday.minusDays(1)),
      previousSessionsResponse(peopleWithSessions),
    )
    caseNotesMockServer.stubUsageByPersonIdentifier(
      transferTypes(prisonCode, prisoners.personIdentifiers(), yesterday.minusMonths(6), yesterday.plusDays(1)),
      transferResponse(prisoners.personIdentifiers()),
    )

    webTestClient
      .post()
      .uri("/prison-statistics/calculate")
      .exchange()
      .expectStatus()
      .is2xxSuccessful

    val stats: PrisonStatistic? =
      await untilCallTo {
        prisonStatisticRepository.findByPrisonCodeAndDate(prisonCode, yesterday)
      } matches { it != null }

    assertThat(stats).isNotNull
    assertThat(stats!!.prisonCode).isEqualTo(prisonCode)
    assertThat(stats.date).isEqualTo(yesterday)

    assertThat(stats.totalPrisoners).isEqualTo(prisoners.size)
    assertThat(stats.eligiblePrisoners).isEqualTo(prisoners.size)

    assertThat(stats.assignedKeyworker).isEqualTo(32)
    assertThat(stats.activeKeyworkers).isEqualTo(6)

    assertThat(stats.keyworkerSessions).isEqualTo(40)
    assertThat(stats.keyworkerEntries).isEqualTo(9)

    assertThat(stats.averageReceptionToAllocationDays).isEqualTo(30)
    assertThat(stats.averageReceptionToSessionDays).isEqualTo(24)
  }

  @Test
  fun `calculate prison statistics for yesterday for a prison with complex needs`() {
    val prisonCode = "CALWIC"
    val yesterday = now().minusDays(1)
    givenPrisonConfig(prisonConfig(prisonCode, true, hasPrisonersWithHighComplexityNeeds = true))
    val keyworkers =
      (0..10).map { index -> givenKeyworkerConfig(keyworkerConfig(if (index % 2 == 0) ACTIVE else INACTIVE, newId())) }
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(keyworkers.map { it.staffId }))
    val prisoners = prisoners()
    prisonerSearchMockServer.stubFindAllPrisoners(prisonCode, prisoners)
    val withComplexNeeds: List<ComplexOffender> = prisoners.personIdentifiers().withComplexNeeds()
    complexityOfNeedMockServer.stubComplexOffenders(prisoners.personIdentifiers(), withComplexNeeds)
    val eligiblePrisoners = (prisoners.personIdentifiers() - withComplexNeeds.map { it.offenderNo })
    eligiblePrisoners.forEachIndexed { index, pi ->
      if (index % 3 == 0) {
        givenKeyworkerAllocation(
          keyworkerAllocation(
            pi,
            prisonCode,
            keyworkers.filter { it.status.code == ACTIVE.name }.random().staffId,
            yesterday.minusDays(index % 10L).atTime(LocalTime.now()),
            allocationType = if (index % 25 == 0) PROVISIONAL else MANUAL,
          ),
        )
      }
    }
    val noteUsageResponse = noteUsageResponse(eligiblePrisoners)
    caseNotesMockServer.stubUsageByPersonIdentifier(
      keyworkerTypes(prisonCode, eligiblePrisoners, yesterday),
      noteUsageResponse,
    )
    val peopleWithSessions = CaseNoteSummary(noteUsageResponse.content).personIdentifiersWithSessions()
    caseNotesMockServer.stubUsageByPersonIdentifier(
      sessionTypes(prisonCode, peopleWithSessions, yesterday.minusMonths(6), yesterday.minusDays(1)),
      previousSessionsResponse(peopleWithSessions),
    )
    caseNotesMockServer.stubUsageByPersonIdentifier(
      transferTypes(prisonCode, eligiblePrisoners, yesterday.minusMonths(6), yesterday.plusDays(1)),
      NoteUsageResponse(emptyMap()),
    )

    webTestClient
      .post()
      .uri("/prison-statistics/calculate")
      .exchange()
      .expectStatus()
      .is2xxSuccessful

    val stats: PrisonStatistic? =
      await untilCallTo {
        prisonStatisticRepository.findByPrisonCodeAndDate(prisonCode, yesterday)
      } matches { it != null }

    assertThat(stats).isNotNull
    assertThat(stats!!.prisonCode).isEqualTo(prisonCode)
    assertThat(stats.date).isEqualTo(yesterday)

    assertThat(stats.totalPrisoners).isEqualTo(prisoners.size)
    assertThat(stats.eligiblePrisoners).isEqualTo(eligiblePrisoners.size)

    assertThat(stats.assignedKeyworker).isEqualTo(25)
    assertThat(stats.activeKeyworkers).isEqualTo(6)

    assertThat(stats.keyworkerSessions).isEqualTo(32)
    assertThat(stats.keyworkerEntries).isEqualTo(7)

    assertThat(stats.averageReceptionToAllocationDays).isNull()
    assertThat(stats.averageReceptionToSessionDays).isNull()
  }

  private fun prisoners(count: Int = 100) =
    Prisoners(
      (0..count).map { index ->
        Prisoner(
          personIdentifier(),
          "First",
          "Last",
          now().minusDays(index / 2 + 1L),
          now().plusDays(index * 2 + 2L),
          "DEF",
          "Default Prison",
          "DEF-A-1",
          "STANDARD",
          null,
          null,
        )
      },
    )

  private fun noteUsageResponse(personIdentifiers: Set<String>): NoteUsageResponse<UsageByPersonIdentifierResponse> =
    NoteUsageResponse(
      personIdentifiers
        .mapIndexed { index, pi ->
          buildSet<UsageByPersonIdentifierResponse> {
            if (index % 3 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  KW_TYPE,
                  SESSION_SUBTYPE,
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
                  ENTRY_SUBTYPE,
                  1,
                  LatestNote(LocalDateTime.now().minusDays(1)),
                ),
              )
            }
          }
        }.flatten()
        .groupBy { it.personIdentifier },
    )

  private fun previousSessionsResponse(personIdentifiers: Set<String>): NoteUsageResponse<UsageByPersonIdentifierResponse> =
    NoteUsageResponse(
      personIdentifiers
        .mapIndexed { index, pi ->
          buildSet<UsageByPersonIdentifierResponse> {
            if (index % 4 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  KW_TYPE,
                  SESSION_SUBTYPE,
                  3 * index / personIdentifiers.size,
                  LatestNote(LocalDateTime.now().minusDays(7)),
                ),
              )
            }
          }
        }.flatten()
        .groupBy { it.personIdentifier },
    )

  private fun transferResponse(personIdentifiers: Set<String>): NoteUsageResponse<UsageByPersonIdentifierResponse> =
    NoteUsageResponse(
      personIdentifiers
        .mapIndexed { index, pi ->
          buildSet<UsageByPersonIdentifierResponse> {
            if (index % 2 == 0) {
              add(
                UsageByPersonIdentifierResponse(
                  pi,
                  TRANSFER_TYPE,
                  "NE1",
                  1,
                  LatestNote(LocalDateTime.now().minusDays(index / 2 + 1L)),
                ),
              )
            }
          }
        }.flatten()
        .groupBy { it.personIdentifier },
    )

  private fun staffRoles(staffIds: List<Long>) =
    staffIds.map {
      StaffLocationRoleDto.builder().staffId(it).build()
    }

  private fun Set<String>.withComplexNeeds() =
    mapIndexedNotNull { index, pi ->
      if (index % 5 == 0) {
        ComplexOffender(pi, ComplexityOfNeedLevel.HIGH)
      } else {
        null
      }
    }
}
