package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonStats
import java.time.LocalDate

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
    val stats =
      (0..500).map {
        prisonStat(
          prisonCode,
          LocalDate.now().minusDays(it.toLong()),
          it % 10 + 100,
          it % 10 + 80,
          it % 10 + 75,
          it % 10 + 1,
          (it % 10 + 1) * 2,
          (it % 10) * 2,
          if (it % 9 == 0) null else it % 10,
          if (it % 7 == 0) null else it % 20,
        )
      }
    prisonStatisticRepository.saveAll(stats)

    val res =
      getPrisonStatsSpec(prisonCode)
        .expectStatus()
        .isOk
        .expectBody(PrisonStats::class.java)
        .returnResult()
        .responseBody

    assertThat(res!!).isNotNull()
    with(res.current) {
      assertThat(this!!).isNotNull()
      assertThat(totalPrisoners).isEqualTo(104)
      assertThat(eligiblePrisoners).isEqualTo(84)
      assertThat(prisonersAssignedKeyworker).isEqualTo(79)
      assertThat(activeKeyworkers).isEqualTo(5)
      assertThat(keyworkerSessions).isEqualTo(334)
      assertThat(keyworkerEntries).isEqualTo(272)
      assertThat(avgReceptionToAllocationDays).isEqualTo(4)
      assertThat(avgReceptionToSessionDays).isEqualTo(8)
      assertThat(projectedSessions).isEqualTo(372)
      assertThat(percentageWithKeyworker).isEqualTo(94.05)
      assertThat(compliance).isEqualTo(89.78)
    }

    with(res.previous) {
      assertThat(this!!).isNotNull()
      assertThat(totalPrisoners).isEqualTo(104)
      assertThat(eligiblePrisoners).isEqualTo(84)
      assertThat(prisonersAssignedKeyworker).isEqualTo(79)
      assertThat(activeKeyworkers).isEqualTo(5)
      assertThat(keyworkerSessions).isEqualTo(330)
      assertThat(keyworkerEntries).isEqualTo(270)
      assertThat(avgReceptionToAllocationDays).isEqualTo(4)
      assertThat(avgReceptionToSessionDays).isEqualTo(10)
      assertThat(projectedSessions).isEqualTo(360)
      assertThat(percentageWithKeyworker).isEqualTo(94.05)
      assertThat(compliance).isEqualTo(91.67)
    }
  }

  private fun getPrisonStatsSpec(
    prisonCode: String,
    from: LocalDate = LocalDate.now().minusMonths(1),
    to: LocalDate = LocalDate.now().minusDays(1),
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
    const val GET_STATS = "/prisons/{prisonCode}/keyworker/statistics"
  }
}
