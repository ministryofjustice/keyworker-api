package uk.gov.justice.digital.hmpps.keyworker.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.dto.ActiveAgenciesResponse
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.keyworker.integration.KeyworkerServiceIntegrationTest.Companion.KEYWORKER_ID_1
import uk.gov.justice.digital.hmpps.keyworker.integration.KeyworkerServiceIntegrationTest.Companion.MIGRATED_ALLOCATION_OFFENDER_ID
import uk.gov.justice.digital.hmpps.keyworker.integration.KeyworkerServiceIntegrationTest.Companion.NON_MIGRATED_ALLOCATION_OFFENDER_ID
import uk.gov.justice.digital.hmpps.keyworker.integration.KeyworkerServiceIntegrationTest.Companion.PRISON_ID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE
import java.util.function.Consumer

class InfoIntTest : IntegrationTest() {
  @Test
  fun `keyworker enabled prisons are returned based on config file`() {
    val prisonCode = "AAP"
    givenPrisonConfig(prisonConfig(prisonCode, true))

    val response =
      webTestClient
        .get()
        .uri("/key-worker/info")
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ActiveAgenciesResponse>()
        .returnResult()
        .responseBody!!

    assertThat(response.activeAgencies).isNotEmpty
    assertThat(response.activeAgencies).contains(prisonCode)
  }

  @Test
  fun `personal officer enabled prisons are returned from the database`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val poPrisonCode = "POP"
    givenPrisonConfig(prisonConfig(poPrisonCode, enabled = true))

    val response =
      webTestClient
        .get()
        .uri("/personal-officer/info")
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<ActiveAgenciesResponse>()
        .returnResult()
        .responseBody!!

    assertThat(response.activeAgencies).contains(poPrisonCode)
  }

  @Test
  fun `Info page contains git information`() {
    webTestClient
      .get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("git.commit.id")
      .isNotEmpty
  }

  @Test
  fun `Info page reports version`() {
    webTestClient
      .get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("build.version")
      .value(
        Consumer<String> {
          assertThat(it).startsWith(LocalDateTime.now().format(ISO_DATE))
        },
      )
  }

  @Test
  fun `Info page still works when items in ehcache`() {
    migratedFoAutoAllocation(PRISON_ID)
    prisonMockServer.stubOffendersAtLocationForAutoAllocation(
      getWiremockResponse(PRISON_ID, "offenders-at-location"),
    )
    prisonMockServer.stubKeyworkerRoles(
      PRISON_ID,
      KEYWORKER_ID_1,
      getWiremockResponse(PRISON_ID, "staff-location-role-list"),
    )
    addKeyworkerAllocation(PRISON_ID, NON_MIGRATED_ALLOCATION_OFFENDER_ID)
    prisonMockServer.stubkeyworkerDetails(KEYWORKER_ID_1, getWiremockResponse("staff-details--5"))
    prisonMockServer.stubOffendersAllocationHistory(getWiremockResponse(PRISON_ID, "offenders-history"))
    prisonMockServer.stubPrisonerStatus(
      NON_MIGRATED_ALLOCATION_OFFENDER_ID,
      getWiremockResponse("prisoners_information_A1234AA"),
    )

    webTestClient
      .get()
      .uri("/key-worker/allocation-history/$NON_MIGRATED_ALLOCATION_OFFENDER_ID")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful

    webTestClient
      .get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("build.version")
      .value<String> {
        assertThat(it).startsWith(LocalDateTime.now().format(ISO_DATE))
      }
  }

  fun addKeyworkerAllocation(
    prisonId: String,
    offenderId: String,
  ) {
    setKeyworkerCapacity(PRISON_ID, KEYWORKER_ID_1, 3)

    webTestClient
      .post()
      .uri("/key-worker/allocate")
      .headers(setOmicAdminHeaders())
      .bodyValue(
        mapOf(
          "offenderNo" to MIGRATED_ALLOCATION_OFFENDER_ID,
          "staffId" to KEYWORKER_ID_1,
          "prisonId" to PRISON_ID,
          "allocationType" to "M",
          "allocationReason" to "MANUAL",
        ),
      ).exchange()
      .expectStatus()
      .is2xxSuccessful
  }
}
