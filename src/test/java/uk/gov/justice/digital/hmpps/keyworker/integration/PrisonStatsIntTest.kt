package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStats
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
    getPrisonStatsSpec("DNM", role = "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok prison stats returned`() {
    val prisonCode = "GST"
    givenPrisonConfig(
      prisonConfig(
        prisonCode,
        frequencyInWeeks = 2,
        hasPrisonersWithHighComplexityNeeds = true,
      ),
    )
    val start = LocalDate.of(2025, 4, 1)
    val end = LocalDate.of(2025, 4, 30)
    val prevStart = LocalDate.of(2025, 3, 2)
    val prevEnd = LocalDate.of(2025, 3, 31)

    val stats =
      buildList {
        var startDate = prevStart
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

    val res =
      getPrisonStatsSpec(prisonCode, start, end)
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
      assertThat(prisonersAssignedKeyworker).isEqualTo(75)
      assertThat(activeKeyworkers).isEqualTo(10)
      assertThat(keyworkerSessions).isEqualTo(147)
      assertThat(keyworkerEntries).isEqualTo(51)
      assertThat(avgReceptionToAllocationDays).isEqualTo(4)
      assertThat(avgReceptionToSessionDays).isEqualTo(11)
      assertThat(projectedSessions).isEqualTo(171)
      assertThat(percentageWithKeyworker).isEqualTo(93.75)
      assertThat(compliance).isEqualTo(85.96)
    }

    with(res.previous) {
      assertThat(this!!).isNotNull()
      assertThat(from).isEqualTo(prevStart)
      assertThat(to).isEqualTo(prevEnd)
      assertThat(totalPrisoners).isEqualTo(90)
      assertThat(highComplexityOfNeedPrisoners).isEqualTo(18)
      assertThat(eligiblePrisoners).isEqualTo(72)
      assertThat(prisonersAssignedKeyworker).isEqualTo(65)
      assertThat(activeKeyworkers).isEqualTo(9)
      assertThat(keyworkerSessions).isEqualTo(147)
      assertThat(keyworkerEntries).isEqualTo(51)
      assertThat(avgReceptionToAllocationDays).isEqualTo(4)
      assertThat(avgReceptionToSessionDays).isEqualTo(8)
      assertThat(projectedSessions).isEqualTo(154)
      assertThat(percentageWithKeyworker).isEqualTo(90.28)
      assertThat(compliance).isEqualTo(95.45)
    }
  }

  private fun getPrisonStatsSpec(
    prisonCode: String,
    from: LocalDate = now().minusDays(29),
    to: LocalDate = now().minusDays(1),
    role: String? = Roles.KEYWORKER_RO,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_STATS)
      it.queryParam("from", from)
      it.queryParam("to", to)
      it.build(prisonCode)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val GET_STATS = "/prisons/{prisonCode}/statistics/keyworker"
  }
}
