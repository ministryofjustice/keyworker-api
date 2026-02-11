package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PolicyEnabled
import uk.gov.justice.digital.hmpps.keyworker.model.prison.PrisonPolicies
import uk.gov.justice.digital.hmpps.keyworker.services.Prison
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

class PrisonPoliciesEnabledIntTest : IntegrationTest() {
  @Test
  fun `401 not authorised without token`() {
    webTestClient
      .get()
      .uri(PRISON_POLICIES_URL, "NEA")
      .exchange()
      .expectStatus()
      .isUnauthorized

    webTestClient
      .put()
      .uri(PRISON_POLICIES_URL, "NEA")
      .bodyValue(PrisonPolicies(emptySet()))
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getPrisonPolicies("NEA", role = "ROLE_NE__OTHER__RW").expectStatus().isForbidden
    setPrisonPolicies("NEA", role = "ROLE_NE__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - prison does not exist`() {
    val prisonCode = "PDE"
    prisonRegisterMockServer.stubGetPrisons(setOf(prisonCode), emptySet())
    val res =
      setPrisonPolicies(prisonCode)
        .expectStatus()
        .isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!
    assertThat(res.developerMessage).isEqualTo("Provided prison code was invalid")
  }

  @Test
  fun `200 ok - can create multiple policies for prison`() {
    val prisonCode = "SPP"
    val pp =
      PrisonPolicies(
        setOf(
          PolicyEnabled(AllocationPolicy.KEY_WORKER, true),
          PolicyEnabled(AllocationPolicy.PERSONAL_OFFICER, true),
        ),
      )
    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, prisonCode)))
    val res =
      setPrisonPolicies(prisonCode, pp, caseloadId = prisonCode)
        .expectStatus()
        .isOk
        .expectBody<PrisonPolicies>()
        .returnResult()
        .responseBody!!

    assertThat(res.policies).containsExactlyInAnyOrderElementsOf(pp.policies)
    val policyNames = pp.policies.map { it.policy.name }.toSet()
    val policies = prisonConfigRepository.findConfigurationsForPolicies(prisonCode, policyNames)
    assertThat(policies.map { it.policy }).containsExactlyInAnyOrderElementsOf(policyNames)

    policies.forEach { config ->
      verifyAudit(
        config,
        config.id,
        RevisionType.ADD,
        setOfNotNull("PrisonConfiguration"),
        AllocationContext.get().copy(username = "keyworker-ui", activeCaseloadId = prisonCode),
      )
    }
  }

  @Test
  fun `200 ok - can update multiple policies for prison`() {
    val prisonCode = "UPP"
    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, prisonCode)))
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    givenPrisonConfig(prisonConfig(prisonCode, true))
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    givenPrisonConfig(prisonConfig(prisonCode, true))
    val activePolicies = prisonConfigRepository.findEnabledPrisonPolicies(prisonCode)
    assertThat(activePolicies).containsExactlyInAnyOrder(
      AllocationPolicy.KEY_WORKER.name,
      AllocationPolicy.PERSONAL_OFFICER.name,
    )

    val pp =
      PrisonPolicies(
        setOf(
          PolicyEnabled(AllocationPolicy.KEY_WORKER, false),
          PolicyEnabled(AllocationPolicy.PERSONAL_OFFICER, false),
        ),
      )
    val res =
      setPrisonPolicies(prisonCode, pp, caseloadId = prisonCode)
        .expectStatus()
        .isOk
        .expectBody<PrisonPolicies>()
        .returnResult()
        .responseBody!!

    assertThat(res.policies).containsExactlyInAnyOrderElementsOf(pp.policies)
    val policyNames = pp.policies.map { it.policy.name }.toSet()
    val policies = prisonConfigRepository.findConfigurationsForPolicies(prisonCode, policyNames)
    assertThat(policies.map { it.policy }).containsExactlyInAnyOrderElementsOf(policyNames)

    policies.forEach { config ->
      assertThat(config.enabled).isFalse
      verifyAudit(
        config,
        config.id,
        RevisionType.MOD,
        setOfNotNull("PrisonConfiguration"),
        AllocationContext.get().copy(username = "keyworker-ui", activeCaseloadId = prisonCode),
      )
    }
  }

  @Test
  fun `200 ok - can retrieve policies for prison`() {
    val prisonCode = "RMP"
    prisonRegisterMockServer.stubGetPrisons(setOf(Prison(prisonCode, prisonCode)))
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    givenPrisonConfig(prisonConfig(prisonCode, true))
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    givenPrisonConfig(prisonConfig(prisonCode, false))
    val activePolicies = prisonConfigRepository.findEnabledPrisonPolicies(prisonCode)
    assertThat(activePolicies).containsExactlyInAnyOrder(AllocationPolicy.KEY_WORKER.name)

    val res =
      getPrisonPolicies(prisonCode)
        .expectStatus()
        .isOk
        .expectBody<PrisonPolicies>()
        .returnResult()
        .responseBody!!

    val expected =
      PrisonPolicies(
        setOf(
          PolicyEnabled(AllocationPolicy.KEY_WORKER, true),
          PolicyEnabled(AllocationPolicy.PERSONAL_OFFICER, false),
        ),
      )
    assertThat(res.policies).containsExactlyInAnyOrderElementsOf(expected.policies)
    val policyNames = expected.policies.map { it.policy.name }.toSet()
    val policies = prisonConfigRepository.findConfigurationsForPolicies(prisonCode, policyNames)
    assertThat(policies.map { it.policy }).containsExactlyInAnyOrderElementsOf(policyNames)
  }

  private fun setPrisonPolicies(
    prisonCode: String,
    request: PrisonPolicies = PrisonPolicies(emptySet()),
    caseloadId: String? = null,
    role: String? = Roles.ALLOCATIONS_UI,
  ): WebTestClient.ResponseSpec {
    val client =
      webTestClient
        .put()
        .uri(PRISON_POLICIES_URL, prisonCode)
        .bodyValue(request)
        .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))

    if (caseloadId != null) {
      client.header(CaseloadIdHeader.NAME, caseloadId)
    }

    return client.exchange()
  }

  private fun getPrisonPolicies(
    prisonCode: String,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .get()
    .uri(PRISON_POLICIES_URL, prisonCode)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val PRISON_POLICIES_URL = "/prisons/{prisonCode}/policies"
  }
}
