package uk.gov.justice.digital.hmpps.keyworker.integration

import org.junit.jupiter.api.Test

class KeyWorkerDeallocationIntegrationTest : IntegrationTest() {
  companion object {
    const val PRISON_ID = "LEI"
    const val OFFENDER_NO_1 = "A1234XY"
    const val OFFENDER_NO_2 = "A6676RS"
  }

  @Test
  fun `Existing Active Offender can be de-allocated`() {
    migrated(PRISON_ID)

    webTestClient
      .put()
      .uri("/key-worker/deallocate/$OFFENDER_NO_1")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus().is2xxSuccessful
  }

  @Test
  fun `De-allocate inactive offender`() {
    migrated(PRISON_ID)

    webTestClient
      .put()
      .uri("/key-worker/deallocate/$OFFENDER_NO_2")
      .headers(setOmicAdminHeaders())
      .exchange()
      .expectStatus().isNotFound
  }

  @Test
  fun `Returns 403 when the correct roles are not sent`() {
    migrated(PRISON_ID)

    webTestClient
      .put()
      .uri("/key-worker/deallocate/$OFFENDER_NO_1")
      .headers(setHeaders())
      .exchange()
      .expectStatus().isForbidden
  }
}
