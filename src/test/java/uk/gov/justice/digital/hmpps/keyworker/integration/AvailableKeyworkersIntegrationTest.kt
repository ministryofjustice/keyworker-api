package uk.gov.justice.digital.hmpps.keyworker.integration

import org.junit.jupiter.api.Test

class AvailableKeyworkersIntegrationTest : IntegrationTest() {

  companion object {
    const val PRISON_ID = "LEI"
  }

  val AVAILABLE_KEYWORKERS = getWiremockResponse(AutoAllocationIntegrationTest.PRISON_ID, "available-keyworkers")

  @Test
  fun `Available keyworkers - decorated with defaults after migration`() {
    eliteMockServer.stubAvailableKeyworkersForAutoAllocation(AutoAllocationIntegrationTest.PRISON_ID, AVAILABLE_KEYWORKERS)

    migratedFoAutoAllocation(PRISON_ID)

    webTestClient.get()
      .uri("/key-worker/$PRISON_ID/available")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$.length()").isEqualTo(4)
      .jsonPath("$[0].agencyId").isEqualTo(PRISON_ID)
      .jsonPath("$[0].autoAllocationAllowed").isEqualTo(true) // no current record in database - default
      .jsonPath("$[0].status").isEqualTo("ACTIVE") // no current record in database - default
      .jsonPath("$[0].capacity").isEqualTo(6) // no current record in database - default
      .jsonPath("$[0].firstName").isEqualTo("HPA") // no allocations migrated for this user
      .jsonPath("$[0].lastName").isEqualTo("AUser")
  }
}
