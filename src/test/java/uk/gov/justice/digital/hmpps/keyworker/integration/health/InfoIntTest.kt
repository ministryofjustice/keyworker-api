package uk.gov.justice.digital.hmpps.keyworker.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.keyworker.integration.KeyworkerServiceIntegrationTest.Companion.KEYWORKER_ID_1
import uk.gov.justice.digital.hmpps.keyworker.integration.KeyworkerServiceIntegrationTest.Companion.MIGRATED_ALLOCATION_OFFENDER_ID
import uk.gov.justice.digital.hmpps.keyworker.integration.KeyworkerServiceIntegrationTest.Companion.NON_MIGRATED_ALLOCATION_OFFENDER_ID
import uk.gov.justice.digital.hmpps.keyworker.integration.KeyworkerServiceIntegrationTest.Companion.PRISON_ID
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_DATE
import java.util.function.Consumer

class InfoIntTest : IntegrationTest
  () {
  @Test
  fun `Info page contains git information`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("git.commit.id").isNotEmpty
  }

  @Test
  fun `Info page reports version`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("build.version").value(
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

    webTestClient.get()
      .uri("/key-worker/allocation-history/$NON_MIGRATED_ALLOCATION_OFFENDER_ID")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus().is2xxSuccessful

    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("build.version").value<String> {
        assertThat(it).startsWith(LocalDateTime.now().format(ISO_DATE))
      }
  }

  fun addKeyworkerAllocation(
    prisonId: String,
    offenderId: String,
  ) {
    setKeyworkerCapacity(PRISON_ID, KEYWORKER_ID_1, 3)

    webTestClient.post()
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
      )
      .exchange()
      .expectStatus().is2xxSuccessful
  }
}
