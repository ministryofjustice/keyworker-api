package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.dto.RecordedEventType
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_SESSION_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.KW_TYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_SUBTYPE
import uk.gov.justice.digital.hmpps.keyworker.integration.casenotes.CaseNote.Companion.PO_ENTRY_TYPE
import java.time.LocalDate
import java.time.LocalDate.now

class PrisonStatsIntTest : IntegrationTest() {
  @Test
  fun `401 unathorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_STATS, "NEP")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getPrisonStatsSpec("DNM", policy = AllocationPolicy.PERSONAL_OFFICER, role = "ROLE_ANY__OTHER_RW")
      .expectStatus()
      .isForbidden
  }

  @ParameterizedTest
  @EnumSource(AllocationPolicy::class)
  fun `200 ok prison stats returned`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "GST"
    givenPrisonConfig(
      prisonConfig(
        prisonCode,
        frequencyInWeeks = 2,
        hasPrisonersWithHighComplexityNeeds = true,
      ),
    )
    val start = LocalDate.of(2025, 6, 5)
    val end = LocalDate.of(2025, 6, 13)
    val prevStart = LocalDate.of(2025, 5, 27)
    val prevEnd = LocalDate.of(2025, 6, 4)

    val stats =
      buildList {
        var startDate = prevStart
        add(startDate.minusDays(1))
        do {
          add(startDate)
          startDate = startDate.plusDays(1)
        } while (!startDate.isAfter(end))
      }.mapIndexed { index, date ->
        prisonStat(
          prisonCode,
          date,
          if (date.isBefore(start)) 90 else 100,
          if (date.isBefore(start)) 18 else 20,
          if (date.isBefore(start)) 72 else 80,
          if (date.isBefore(start)) 65 else 75,
          if (date.isBefore(start)) 9 else 10,
          if (index % 2 == 0) {
            5
          } else if (index % 5 == 0) {
            0
          } else {
            6
          },
          if (index % 2 == 0) {
            2
          } else if (index % 5 == 0) {
            3
          } else {
            1
          },
          if (index % 9 == 0) null else index % 10,
          if (index % 7 == 0) null else index % 20,
        )
      }
    prisonStatisticRepository.saveAll(stats)

    val currentDateRange = dateRange(start, end)
    val prevDateRange = dateRange(prevStart, prevEnd)
    val (entryType, entrySubtype) =
      when (policy) {
        AllocationPolicy.KEY_WORKER -> KW_TYPE to KW_ENTRY_SUBTYPE
        AllocationPolicy.PERSONAL_OFFICER -> PO_ENTRY_TYPE to PO_ENTRY_SUBTYPE
      }
    (1..16).map {
      givenAllocationCaseNote(caseNote(prisonCode, entryType, entrySubtype, currentDateRange.random().atStartOfDay()))
    }
    (1..15).map {
      givenAllocationCaseNote(caseNote(prisonCode, entryType, entrySubtype, prevDateRange.random().atStartOfDay()))
    }

    if (policy == AllocationPolicy.KEY_WORKER) {
      (1..43).map {
        givenAllocationCaseNote(
          caseNote(
            prisonCode,
            KW_TYPE,
            KW_SESSION_SUBTYPE,
            currentDateRange.random().atStartOfDay(),
          ),
        )
      }
      (1..44).map {
        givenAllocationCaseNote(
          caseNote(
            prisonCode,
            KW_TYPE,
            KW_SESSION_SUBTYPE,
            prevDateRange.random().atStartOfDay(),
          ),
        )
      }
    }

    val res =
      getPrisonStatsSpec(prisonCode, start, end, policy)
        .expectStatus()
        .isOk
        .expectBody(PrisonStats::class.java)
        .returnResult()
        .responseBody

    assertThat(res!!).isNotNull()
    with(res.current) {
      assertThat(this!!).isNotNull()
      assertThat(from).isEqualTo(start)
      assertThat(to).isEqualTo(end)
      assertThat(totalPrisoners).isEqualTo(100)
      assertThat(highComplexityOfNeedPrisoners).isEqualTo(20)
      assertThat(eligiblePrisoners).isEqualTo(80)
      assertThat(prisonersAssigned).isEqualTo(75)
      assertThat(eligibleStaff).isEqualTo(10)
      if (policy == AllocationPolicy.KEY_WORKER) {
        assertThat(recordedEvents.find { it.type == RecordedEventType.SESSION }?.count).isEqualTo(43)
        assertThat(recordedEvents.find { it.type == RecordedEventType.ENTRY }?.count).isEqualTo(16)
      } else {
        assertThat(recordedEvents.find { it.type == RecordedEventType.ENTRY }?.count).isEqualTo(16)
        assertThat(recordedEvents.find { it.type == RecordedEventType.SESSION }?.count).isNull()
      }
      assertThat(avgReceptionToAllocationDays).isEqualTo(3)
      assertThat(avgReceptionToRecordedEventDays).isEqualTo(14)
      assertThat(projectedRecordedEvents).isEqualTo(51)
      assertThat(percentageAssigned).isEqualTo(93.75)
      assertThat(recordedEventComplianceRate).isEqualTo(84.31)
    }

    with(res.previous) {
      assertThat(this!!).isNotNull()
      assertThat(from).isEqualTo(prevStart)
      assertThat(to).isEqualTo(prevEnd)
      assertThat(totalPrisoners).isEqualTo(90)
      assertThat(highComplexityOfNeedPrisoners).isEqualTo(18)
      assertThat(eligiblePrisoners).isEqualTo(72)
      assertThat(prisonersAssigned).isEqualTo(65)
      assertThat(eligibleStaff).isEqualTo(9)
      assertThat(recordedEvents.find { it.type == RecordedEventType.ENTRY }?.count).isEqualTo(15)
      if (policy == AllocationPolicy.KEY_WORKER) {
        assertThat(recordedEvents.find { it.type == RecordedEventType.SESSION }?.count).isEqualTo(44)
      }
      assertThat(avgReceptionToAllocationDays).isEqualTo(4)
      assertThat(avgReceptionToRecordedEventDays).isEqualTo(4)
      assertThat(projectedRecordedEvents).isEqualTo(46)
      assertThat(percentageAssigned).isEqualTo(90.28)
      assertThat(recordedEventComplianceRate).isEqualTo(95.65)
    }
  }

  private fun getPrisonStatsSpec(
    prisonCode: String,
    from: LocalDate = now().minusDays(29),
    to: LocalDate = now().minusDays(1),
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_STATS)
      it.queryParam("from", from)
      it.queryParam("to", to)
      it.build(prisonCode)
    }.headers(setHeaders(username = "allocations-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  companion object {
    const val GET_STATS = "/prisons/{prisonCode}/statistics"
  }
}
