package uk.gov.justice.digital.hmpps.keyworker.integration

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class KeyworkerDetailsIntegrationTest : IntegrationTest() {
  companion object {
    const val PRISON_ID = "LEI"
    const val STAFF_ID = -5L
  }

  val sTAFFLOCATIONROLELIST = getWiremockResponse(PRISON_ID, "staff-location-role-list")
  val sTAFFDETAILS = getWiremockResponse("staff-details-$STAFF_ID")

  @Disabled
  @Test
  fun `key worker details happy path`() {
    migrated(PRISON_ID)

    prisonMockServer.stubKeyworkerRoles(PRISON_ID, STAFF_ID, sTAFFLOCATIONROLELIST)

    webTestClient
      .get()
      .uri("/key-worker/$STAFF_ID/prison/$PRISON_ID")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful
      .expectBody()
      .jsonPath("$.agencyId")
      .isEqualTo("LEI")
      .jsonPath("$.autoAllocationAllowed")
      .isEqualTo(true) // no current record in database - default
      .jsonPath("$.status")
      .isEqualTo("ACTIVE") // no current record in database - default
      .jsonPath("$.capacity")
      .isEqualTo(6) // no current record in database - default
      .jsonPath("$.numberAllocated")
      .isEqualTo(3) // after migration -5 has 3 active allocations
      .jsonPath("$.firstName")
      .isEqualTo("Another")
      .jsonPath("$.lastName")
      .isEqualTo("CUser")
      .jsonPath("$.activeDate")
      .doesNotExist()
  }

  @Test
  fun `key worker details - keyworker not available for prison - defaults to retrieve basic details (from other prison)`() {
    migrated(PRISON_ID)

    // lookup for prison fails to retrieve the keyworker details  (no longer working for current agency)
    prisonMockServer.stubKeyworkerRoles(PRISON_ID, STAFF_ID, "[]")
    prisonMockServer.stubkeyworkerDetails(STAFF_ID, sTAFFDETAILS)

    webTestClient
      .get()
      .uri("/key-worker/$STAFF_ID/prison/$PRISON_ID")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus()
      .is2xxSuccessful()
      .expectBody()
      .jsonPath(
        "$.agencyId",
      ).doesNotExist()
      // basic details do not return agency id - retrieving these details to enable displaying of keyworker name
      .jsonPath("$.numberAllocated")
      .doesNotExist() // unable to determine allocations without agencyId
      .jsonPath("$.firstName")
      .isEqualTo("Another")
      .jsonPath("$.lastName")
      .isEqualTo("CUser")
  }
}
