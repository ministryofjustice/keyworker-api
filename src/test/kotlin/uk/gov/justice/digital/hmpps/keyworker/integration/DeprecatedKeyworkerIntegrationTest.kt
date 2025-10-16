package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.controllers.BasicKeyworkerInfo
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDateTime

class DeprecatedKeyworkerIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(DEPRECATED_KEYWORKER_URL, personIdentifier())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `404 not found when person identifier does not exist`() {
    val personIdentifier = personIdentifier()
    prisonerSearchMockServer.stubFindPrisonerDetails("KNE", setOf(personIdentifier), emptyList())

    getDeprecatedKeyworkerSpec(personIdentifier).expectStatus().isNotFound
  }

  @Test
  fun `200 ok and current keyworker allocation returned`() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))

    val prisonCode = "DKR"
    val personIdentifier = personIdentifier()
    givenPrisonConfig(prisonConfig(prisonCode, true))

    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, "Description of $prisonCode")))
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, setOf(personIdentifier))

    val current = givenStaffConfig(staffConfig(StaffStatus.ACTIVE, capacity = 10))
    val currentAllocation =
      givenAllocation(
        staffAllocation(
          personIdentifier = personIdentifier,
          prisonCode = prisonCode,
          staffId = current.staffId,
          allocatedAt = LocalDateTime.now(),
          active = true,
          allocatedBy = "A110C473",
        ),
      )
    prisonMockServer.stubStaffSummaries(
      listOf(
        staffSummary("Current", "Keyworker", currentAllocation.staffId),
      ),
    )
    prisonMockServer.stubStaffEmail(currentAllocation.staffId, "current-staff@justice.gov.uk")

    val response =
      getDeprecatedKeyworkerSpec(personIdentifier)
        .expectStatus()
        .isOk
        .expectBody(BasicKeyworkerInfo::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.staffId).isEqualTo(current.staffId)
    assertThat(response.email).isEqualTo("current-staff@justice.gov.uk")
  }

  private fun getDeprecatedKeyworkerSpec(personIdentifier: String): WebTestClient.ResponseSpec =
    webTestClient
      .get()
      .uri {
        it.path(DEPRECATED_KEYWORKER_URL)
        it.build(personIdentifier)
      }.headers(setHeaders(username = "dps-prisoner-profile", roles = listOf()))
      .exchange()

  fun staffSummary(
    firstName: String = "First",
    lastName: String = "Last",
    id: Long = newId(),
  ): StaffSummary = StaffSummary(id, firstName, lastName)

  companion object {
    const val DEPRECATED_KEYWORKER_URL = "/key-worker/offender/{personIdentifier}"
  }
}
