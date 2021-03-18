package uk.gov.justice.digital.hmpps.keyworker.integration

import org.junit.jupiter.api.Test

class KeyworkerSearchIntegrationTest : IntegrationTest() {
  companion object {
    const val PRISON_ID = "LEI"
    const val USERNAME = "Another"
  }

  val STAFF_DETAILS = getWiremockResponse(PRISON_ID, "staff-location-role-list")
  val CASE_NOTE_USAGE = getWiremockResponse("case-note-usage")

  @Test
  fun `keyworker search - decorated with defaults after migration`() {
    migrated(PRISON_ID)

    eliteMockServer.stubKeyworkerSearch(PRISON_ID, USERNAME, STAFF_DETAILS, 4)
    eliteMockServer.stubCaseNoteUsage(CASE_NOTE_USAGE)

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