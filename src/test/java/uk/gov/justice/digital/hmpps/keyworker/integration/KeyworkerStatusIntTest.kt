package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.keyworker.services.UsernameKeyworker

class KeyworkerStatusIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised`() {
    webTestClient
      .get()
      .uri(KEYWORKER_STATUS_URL, "prisonCode", "username")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden`() {
    getKeyworkerStatusSpec("ANYONE", role = null).expectStatus().isForbidden
  }

  @Test
  fun `404 not found - staff is not found`() {
    val username = "test"
    val userId = "1234"
    manageUsersMockServer.stubGetUserDetails(username, userId, "Test user")
    prisonMockServer.stubStaffIsKeyworker(userId, DEFAULT_PRISON_CODE, true, HttpStatus.NOT_FOUND)
    getKeyworkerStatusSpec(username).expectStatus().isNotFound
  }

  @Test
  fun `400 bad request - user not recognised`() {
    val username = "not.found"
    manageUsersMockServer.stubGetUserDetailsNotFound(username)
    getKeyworkerStatusSpec(username).expectStatus().isBadRequest
  }

  @Test
  fun `200 ok - staff is keyworker`() {
    val username = "mr.smith"
    val userId = "2345"
    manageUsersMockServer.stubGetUserDetails(username, userId, "Mr Smith")
    prisonMockServer.stubStaffIsKeyworker(userId, DEFAULT_PRISON_CODE, true)
    val res =
      getKeyworkerStatusSpec(username)
        .expectStatus()
        .isOk
        .expectBody(UsernameKeyworker::class.java)
        .returnResult()
        .responseBody
    assertThat(res?.isKeyworker).isTrue()
  }

  @Test
  fun `200 ok - staff is not keyworker`() {
    val username = "mrs.smith"
    val userId = "4567"
    manageUsersMockServer.stubGetUserDetails(username, userId, "Mrs Smith")
    prisonMockServer.stubStaffIsKeyworker(userId, DEFAULT_PRISON_CODE, false)
    val res =
      getKeyworkerStatusSpec(username)
        .expectStatus()
        .isOk
        .expectBody(UsernameKeyworker::class.java)
        .returnResult()
        .responseBody
    assertThat(res?.isKeyworker).isFalse()
  }

  private fun getKeyworkerStatusSpec(
    username: String,
    prisonCode: String = DEFAULT_PRISON_CODE,
    role: String? = "ROLE_KEY_WORKER__RO",
  ) = webTestClient
    .get()
    .uri(KEYWORKER_STATUS_URL, prisonCode, username)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val KEYWORKER_STATUS_URL = "/prisons/{prisonCode}/key-workers/{username}/status"
    const val DEFAULT_PRISON_CODE = "MDI"
  }
}
