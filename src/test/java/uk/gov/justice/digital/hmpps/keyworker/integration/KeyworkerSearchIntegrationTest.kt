package uk.gov.justice.digital.hmpps.keyworker.integration

import org.junit.jupiter.api.Disabled

class KeyworkerSearchIntegrationTest : IntegrationTest() {
  companion object {
    const val PRISON_ID = "LEI"
    const val USERNAME = "Another"
  }

  val sTAFFDETAILS = getWiremockResponse(PRISON_ID, "staff-location-role-list")
  val cASENOTEUSAGE = getWiremockResponse("case-note-usage")

  @Disabled
  fun `keyworker search - decorated with defaults after migration`() {
    migrated(PRISON_ID)

    prisonMockServer.stubKeyworkerSearch(PRISON_ID, USERNAME, sTAFFDETAILS, 4)
    prisonMockServer.stubCaseNoteUsage(cASENOTEUSAGE)

    webTestClient
      .get()
      .uri("/key-worker/$PRISON_ID/members?nameFilter=$USERNAME&statusFilter=ACTIVE")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus().is2xxSuccessful
      .expectBody()
      .jsonPath("$[0].lastName").isEqualTo(USERNAME)
      .jsonPath("$[0].capacity").isEqualTo(6)
      .jsonPath("$[0].numberAllocated").isEqualTo(3)
      .jsonPath("$[0].numKeyWorkerSessions").isEqualTo(4)
  }
}
