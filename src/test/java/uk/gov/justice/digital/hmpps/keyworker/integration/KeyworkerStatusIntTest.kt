package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.services.UsernameKeyworker
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.staffLocationRole
import java.time.LocalDate

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
    getKeyworkerStatusSpec("ANYONE", "FNP", role = null).expectStatus().isForbidden
  }

  @Test
  fun `404 not found - staff is not found`() {
    val username = "test"
    val userId = "1234"
    manageUsersMockServer.stubGetUserDetails(username, userId, "Test user")
    getKeyworkerStatusSpec(username, "NFP").expectStatus().isNotFound
  }

  @Test
  fun `400 bad request - user not recognised`() {
    val username = "not.found"
    manageUsersMockServer.stubGetUserDetailsNotFound(username)
    getKeyworkerStatusSpec(username, "NFP").expectStatus().isBadRequest
  }

  @Test
  fun `200 ok - staff is keyworker`() {
    val prisonCode = "SIK"
    val username = "mr.smith"
    val userId = "2345"
    manageUsersMockServer.stubGetUserDetails(username, userId, "Mr Smith")
    prisonMockServer.stubKeyworkerDetails(prisonCode, userId.toLong(), staffLocationRole(userId.toLong()))
    val res =
      getKeyworkerStatusSpec(username, prisonCode)
        .expectStatus()
        .isOk
        .expectBody(UsernameKeyworker::class.java)
        .returnResult()
        .responseBody
    assertThat(res?.isKeyworker).isTrue()
  }

  @Test
  fun `200 ok - staff is expired keyworker`() {
    val prisonCode = "SNK"
    val username = "mrs.smith"
    val userId = "4567"
    manageUsersMockServer.stubGetUserDetails(username, userId, "Mrs Smith")
    prisonMockServer.stubKeyworkerDetails(prisonCode, userId.toLong(), staffLocationRole(userId.toLong(), LocalDate.now().minusDays(1)))
    val res =
      getKeyworkerStatusSpec(username, prisonCode)
        .expectStatus()
        .isOk
        .expectBody(UsernameKeyworker::class.java)
        .returnResult()
        .responseBody
    assertThat(res?.isKeyworker).isFalse()
  }

  @Test
  fun `200 ok - staff is not a keyworker`() {
    val prisonCode = "SNK"
    val username = "mrs.smith"
    val userId = "4567"
    manageUsersMockServer.stubGetUserDetails(username, userId, "Mrs Smith")
    prisonMockServer.stubKeyworkerDetails(prisonCode, userId.toLong(), null)
    val res =
      getKeyworkerStatusSpec(username, prisonCode)
        .expectStatus()
        .isOk
        .expectBody(UsernameKeyworker::class.java)
        .returnResult()
        .responseBody
    assertThat(res?.isKeyworker).isFalse()
  }

  private fun getKeyworkerStatusSpec(
    username: String,
    prisonCode: String,
    role: String? = Roles.KEYWORKER_RO,
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
