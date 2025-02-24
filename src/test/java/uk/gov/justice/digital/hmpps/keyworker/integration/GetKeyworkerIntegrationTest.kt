package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.dto.CodedDescription
import uk.gov.justice.digital.hmpps.keyworker.dto.Keyworker
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerDetails
import uk.gov.justice.digital.hmpps.keyworker.model.KeyworkerStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.prisonNumber

class GetKeyworkerIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unathorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_KEYWORKER_DETAILS, "NEP", newId())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getKeyworkerSpec("DNM", newId(), role = "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok and keyworker details returned`() {
    val prisonCode = "DEF"
    val keyworker = givenKeyworker(keyworker(KeyworkerStatus.ACTIVE, capacity = 10))
    prisonMockServer.stubKeyworkerSearch(prisonCode, listOf())
    prisonMockServer.stubKeyworkerDetails(keyworker.staffId)

    val allocations =
      (0..5).map {
        givenKeyworkerAllocation(keyworkerAllocation(prisonNumber(), prisonCode, keyworker.staffId, active = it % 4 != 0))
      }

    prisonerSearchMockServer.stubFindPrisonDetails(allocations.map { it.personIdentifier }.toSet())

    val response =
      getKeyworkerSpec(prisonCode, keyworker.staffId)
        .expectStatus()
        .isOk
        .expectBody(KeyworkerDetails::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.keyworker).isEqualTo(Keyworker(keyworker.staffId, "First", "Last"))
    assertThat(response.status).isEqualTo(CodedDescription("ACT", "Active"))
    assertThat(response.prison).isEqualTo(CodedDescription("DEF", "Default Prison"))
    assertThat(response.capacity).isEqualTo(10)
    assertThat(response.allocated).isEqualTo(4)
    assertThat(response.allocations.size).isEqualTo(4)
  }

  private fun getKeyworkerSpec(
    prisonCode: String,
    staffId: Long,
    role: String? = Roles.KEYWORKER_RO,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_KEYWORKER_DETAILS)
      it.build(prisonCode, staffId)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val GET_KEYWORKER_DETAILS = "/prisons/{prisonCode}/keyworkers/{staffId}"
  }
}
