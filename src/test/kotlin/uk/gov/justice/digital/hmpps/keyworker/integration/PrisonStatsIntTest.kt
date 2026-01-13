package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonStats
import uk.gov.justice.digital.hmpps.keyworker.model.staff.RecordedEventType
import java.time.LocalDate
import java.time.LocalDate.now

class PrisonStatsIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
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
  fun `200 ok - when stats do not exist for date ranges`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "NSP"
    givenPrisonConfig(prisonConfig(prisonCode, true))
    val start = LocalDate.of(2021, 6, 5)
    val end = LocalDate.of(2021, 6, 13)
    val prevStart = LocalDate.of(2021, 5, 27)
    val prevEnd = LocalDate.of(2021, 6, 4)

    val res =
      getPrisonStatsSpec(prisonCode, start, end, prevStart, prevEnd, policy)
        .expectStatus()
        .isOk
        .expectBody<PrisonStats>()
        .returnResult()
        .responseBody

    with(requireNotNull(res).current) {
      assertThat(from).isEqualTo(start)
      assertThat(to).isEqualTo(end)
      assertThat(totalPrisoners).isNull()
      assertThat(highComplexityOfNeedPrisoners).isNull()
      assertThat(eligiblePrisoners).isNull()
      assertThat(prisonersAssigned).isNull()
      assertThat(eligibleStaff).isNull()
      if (policy == AllocationPolicy.KEY_WORKER) {
        assertThat(recordedEvents.find { it.type == RecordedEventType.SESSION }?.count).isEqualTo(0)
      } else {
        assertThat(recordedEvents.find { it.type == RecordedEventType.SESSION }?.count).isNull()
      }
      assertThat(recordedEvents.find { it.type == RecordedEventType.ENTRY }?.count).isEqualTo(0)
      assertThat(avgReceptionToAllocationDays).isNull()
      assertThat(avgReceptionToRecordedEventDays).isNull()
      assertThat(projectedRecordedEvents).isNull()
      assertThat(percentageAssigned).isNull()
      assertThat(recordedEventComplianceRate).isNull()
    }
    with(res.previous) {
      assertThat(from).isEqualTo(prevStart)
      assertThat(to).isEqualTo(prevEnd)
      assertThat(totalPrisoners).isNull()
      assertThat(highComplexityOfNeedPrisoners).isNull()
      assertThat(eligiblePrisoners).isNull()
      assertThat(prisonersAssigned).isNull()
      assertThat(eligibleStaff).isNull()
      if (policy == AllocationPolicy.KEY_WORKER) {
        assertThat(recordedEvents.find { it.type == RecordedEventType.SESSION }?.count).isEqualTo(0)
      } else {
        assertThat(recordedEvents.find { it.type == RecordedEventType.SESSION }?.count).isNull()
      }
      assertThat(recordedEvents.find { it.type == RecordedEventType.ENTRY }?.count).isEqualTo(0)
      assertThat(avgReceptionToAllocationDays).isNull()
      assertThat(avgReceptionToRecordedEventDays).isNull()
      assertThat(projectedRecordedEvents).isNull()
      assertThat(percentageAssigned).isNull()
      assertThat(recordedEventComplianceRate).isNull()
    }
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
          if (index % 9 == 0) null else index % 10,
          if (index % 7 == 0) null else index % 20,
        )
      }
    prisonStatisticRepository.saveAll(stats)

    val currentDateRange = dateRange(start, end)
    val prevDateRange = dateRange(prevStart, prevEnd)
    (1..16).forEach { _ ->
      givenRecordedEvent(recordedEvent(prisonCode, RecordedEventType.ENTRY, currentDateRange.random().atStartOfDay()))
    }
    (1..15).forEach { _ ->
      givenRecordedEvent(recordedEvent(prisonCode, RecordedEventType.ENTRY, prevDateRange.random().atStartOfDay()))
    }

    if (policy == AllocationPolicy.KEY_WORKER) {
      (1..43).forEach { _ ->
        givenRecordedEvent(
          recordedEvent(
            prisonCode,
            RecordedEventType.SESSION,
            currentDateRange.random().atStartOfDay(),
          ),
        )
      }
      (1..44).forEach { _ ->
        givenRecordedEvent(
          recordedEvent(
            prisonCode,
            RecordedEventType.SESSION,
            prevDateRange.random().atStartOfDay(),
          ),
        )
      }
    }

    val res =
      getPrisonStatsSpec(prisonCode, start, end, prevStart, prevEnd, policy)
        .expectStatus()
        .isOk
        .expectBody<PrisonStats>()
        .returnResult()
        .responseBody

    assertThat(res!!).isNotNull()
    with(res.current) {
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
      if (policy == AllocationPolicy.KEY_WORKER) {
        assertThat(recordedEventComplianceRate).isEqualTo(84.31)
      } else {
        assertThat(recordedEventComplianceRate).isNull()
      }
    }

    with(res.previous) {
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
      if (policy == AllocationPolicy.KEY_WORKER) {
        assertThat(recordedEventComplianceRate).isEqualTo(95.65)
      } else {
        assertThat(recordedEventComplianceRate).isNull()
      }
    }
  }

  private fun getPrisonStatsSpec(
    prisonCode: String,
    from: LocalDate = now().minusDays(29),
    to: LocalDate = now().minusDays(1),
    comparisonFrom: LocalDate = now().minusDays(58),
    comparisonTo: LocalDate = now().minusDays(30),
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_STATS)
      it.queryParam("from", from)
      it.queryParam("to", to)
      it.queryParam("comparisonFrom", comparisonFrom)
      it.queryParam("comparisonTo", comparisonTo)
      it.build(prisonCode)
    }.headers(setHeaders(username = "allocations-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  companion object {
    const val GET_STATS = "/prisons/{prisonCode}/statistics"
  }
}
