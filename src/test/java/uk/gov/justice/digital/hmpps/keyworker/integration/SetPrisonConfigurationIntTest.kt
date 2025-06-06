package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.PrisonConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonConfigResponse

class SetPrisonConfigurationIntTest : IntegrationTest() {
  @AfterEach
  fun resetContext() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
  }

  @Test
  fun `401 not authorised without token`() {
    webTestClient
      .put()
      .uri(SET_CONFIG_URL, "NE1")
      .bodyValue(prisonConfigRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    setPrisonConfig("WOR", role = "ROLE_NE__OTHER__RW").expectStatus().isForbidden
  }

  @ParameterizedTest
  @MethodSource("setPrisonConfigurationsValidation")
  fun `400 bad request - validation failures`(
    prisonConfigRequest: PrisonConfigRequest,
    message: String,
  ) {
    val res =
      setPrisonConfig("PRV", prisonConfigRequest)
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo(message)
  }

  @Test
  fun `can create a new prison configurations`() {
    val prisonCode = "ALC"
    val kwContext =
      AllocationContext.get().copy("keyworker-ui", activeCaseloadId = prisonCode, policy = AllocationPolicy.KEY_WORKER)
    val poContext = kwContext.copy(policy = AllocationPolicy.PERSONAL_OFFICER)
    val kRequest = prisonConfigRequest(capacity = 10, frequencyInWeeks = 4)
    val kConfig = setPrisonConfig(prisonCode, kRequest, policy = AllocationPolicy.KEY_WORKER).asPrisonConfig()
    val pRequest = prisonConfigRequest(capacity = 5, frequencyInWeeks = 1)
    val pConfig = setPrisonConfig(prisonCode, pRequest, policy = AllocationPolicy.PERSONAL_OFFICER).asPrisonConfig()

    kConfig.verifyAgainst(kRequest)
    pConfig.verifyAgainst(pRequest)

    setContext(kwContext)
    val kwConfig = requireNotNull(prisonConfigRepository.findByCode(prisonCode))

    setContext(poContext)
    val poConfig = requireNotNull(prisonConfigRepository.findByCode(prisonCode))

    kwConfig.verifyAgainst(kRequest)
    poConfig.verifyAgainst(pRequest)

    verifyAudit(kwConfig, kwConfig.id, RevisionType.ADD, setOf(PrisonConfiguration::class.simpleName!!), kwContext)
    verifyAudit(poConfig, poConfig.id, RevisionType.ADD, setOf(PrisonConfiguration::class.simpleName!!), poContext)
  }

  @Test
  fun `can update existing prison configurations`() {
    val prisonCode = "ALU"
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    givenPrisonConfig(prisonConfig(prisonCode))
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    givenPrisonConfig(prisonConfig(prisonCode, policy = AllocationPolicy.PERSONAL_OFFICER))

    val kRequest = prisonConfigRequest(capacity = 10, frequencyInWeeks = 4)
    val kConfig = setPrisonConfig(prisonCode, kRequest, policy = AllocationPolicy.KEY_WORKER).asPrisonConfig()
    val pRequest = prisonConfigRequest(capacity = 5, frequencyInWeeks = 1)
    val pConfig = setPrisonConfig(prisonCode, pRequest, policy = AllocationPolicy.PERSONAL_OFFICER).asPrisonConfig()

    kConfig.verifyAgainst(kRequest)
    pConfig.verifyAgainst(pRequest)

    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    val kwConfig = requireNotNull(prisonConfigRepository.findByCode(prisonCode))

    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    val poConfig = requireNotNull(prisonConfigRepository.findByCode(prisonCode))

    kwConfig.verifyAgainst(kRequest)
    poConfig.verifyAgainst(pRequest)

    val kwContext = AllocationContext("keyworker-ui", activeCaseloadId = prisonCode, policy = AllocationPolicy.KEY_WORKER)
    val poContext = kwContext.copy(policy = AllocationPolicy.PERSONAL_OFFICER)
    verifyAudit(kwConfig, kwConfig.id, RevisionType.MOD, setOf(PrisonConfiguration::class.simpleName!!), kwContext)
    verifyAudit(poConfig, poConfig.id, RevisionType.MOD, setOf(PrisonConfiguration::class.simpleName!!), poContext)
  }

  private fun setPrisonConfig(
    prisonCode: String,
    request: PrisonConfigRequest = prisonConfigRequest(),
    policy: AllocationPolicy = AllocationPolicy.KEY_WORKER,
    caseloadId: String = prisonCode,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .put()
    .uri(SET_CONFIG_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .header(CaseloadIdHeader.NAME, caseloadId)
    .exchange()

  private fun WebTestClient.ResponseSpec.asPrisonConfig() =
    expectStatus()
      .isOk
      .expectBody(PrisonConfigResponse::class.java)
      .returnResult()
      .responseBody!!

  companion object {
    const val SET_CONFIG_URL = "/prisons/{prisonCode}/configurations"

    private fun prisonConfigRequest(
      isEnabled: Boolean = true,
      allowAutoAllocation: Boolean = true,
      capacity: Int = 9,
      frequencyInWeeks: Int = 1,
      hasPrisonersWithHighComplexityNeeds: Boolean = false,
    ) = PrisonConfigRequest(
      isEnabled,
      allowAutoAllocation,
      capacity,
      frequencyInWeeks,
      hasPrisonersWithHighComplexityNeeds,
    )

    @JvmStatic
    fun setPrisonConfigurationsValidation() =
      listOf(
        Arguments.of(
          prisonConfigRequest(capacity = -1, frequencyInWeeks = -2),
          """
          |Validation failures: 
          |capacity must be greater than 0
          |frequency in weeks must be greater than 0
          |
          """.trimMargin(),
        ),
      )
  }
}

private fun PrisonConfigResponse.verifyAgainst(request: PrisonConfigRequest) {
  assertThat(isEnabled).isEqualTo(request.isEnabled)
  assertThat(capacity).isEqualTo(request.capacity)
  assertThat(frequencyInWeeks).isEqualTo(request.frequencyInWeeks)
  assertThat(allowAutoAllocation).isEqualTo(request.allowAutoAllocation)
  assertThat(hasPrisonersWithHighComplexityNeeds).isEqualTo(request.hasPrisonersWithHighComplexityNeeds)
}

private fun PrisonConfiguration.verifyAgainst(request: PrisonConfigRequest) {
  assertThat(enabled).isEqualTo(request.isEnabled)
  assertThat(maximumCapacity).isEqualTo(request.capacity)
  assertThat(frequencyInWeeks).isEqualTo(request.frequencyInWeeks)
  assertThat(allowAutoAllocation).isEqualTo(request.allowAutoAllocation)
  assertThat(hasPrisonersWithHighComplexityNeeds).isEqualTo(request.hasPrisonersWithHighComplexityNeeds)
}
