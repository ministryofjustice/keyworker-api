package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.AllocationOrder
import uk.gov.justice.digital.hmpps.keyworker.dto.PrisonConfigResponse

class PrisonConfigIntTest : IntegrationTest() {
  @BeforeEach
  fun setUp() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    listOf(
      prisonConfig("ZEZE", false, false, hasPrisonersWithHighComplexityNeeds = false),
      prisonConfig("ZEON", false, false, hasPrisonersWithHighComplexityNeeds = true),
      prisonConfig("ONZE", true, true, hasPrisonersWithHighComplexityNeeds = false),
      prisonConfig("ONON", true, true, hasPrisonersWithHighComplexityNeeds = true),
    ).map { givenPrisonConfig(it) }

    setContext(AllocationContext.get().copy(policy = AllocationPolicy.PERSONAL_OFFICER))
    listOf(
      prisonConfig(
        "ZEZE",
        true,
        true,
        hasPrisonersWithHighComplexityNeeds = true,
        policy = AllocationPolicy.PERSONAL_OFFICER,
      ),
      prisonConfig(
        "ZEON",
        true,
        true,
        hasPrisonersWithHighComplexityNeeds = false,
        policy = AllocationPolicy.PERSONAL_OFFICER,
      ),
      prisonConfig(
        "ONZE",
        false,
        false,
        hasPrisonersWithHighComplexityNeeds = true,
        policy = AllocationPolicy.PERSONAL_OFFICER,
      ),
      prisonConfig(
        "ONON",
        false,
        false,
        hasPrisonersWithHighComplexityNeeds = false,
        policy = AllocationPolicy.PERSONAL_OFFICER,
      ),
    ).map { givenPrisonConfig(it) }
  }

  @ParameterizedTest
  @MethodSource("prisonConfigArgs")
  fun `200 ok - prison config is returned appropriately`(
    prisonCode: String,
    policy: AllocationPolicy,
    status: PrisonConfigResponse,
  ) {
    val res = getPrisonConfig(prisonCode, policy)
    assertThat(res).isEqualTo(status)
  }

  @Test
  fun `allocation order configuration is persisted and retrieved correctly`() {
    val prisonCode = "MDI"

    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    val byAllocationsConfig =
      prisonConfig(
        prisonCode,
        enabled = true,
        allowAutoAllocation = true,
        allocationOrder = AllocationOrder.BY_ALLOCATIONS,
      )
    givenPrisonConfig(byAllocationsConfig)

    val byAllocationsResponse = getPrisonConfig(prisonCode, AllocationPolicy.KEY_WORKER)

    assertThat(byAllocationsResponse.allocationOrder).isEqualTo(AllocationOrder.BY_ALLOCATIONS)

    val byNameConfig =
      prisonConfig(
        prisonCode,
        enabled = true,
        allowAutoAllocation = true,
        allocationOrder = AllocationOrder.BY_NAME,
      )
    givenPrisonConfig(byNameConfig)

    val byNameResponse = getPrisonConfig(prisonCode, AllocationPolicy.KEY_WORKER)

    assertThat(byNameResponse.allocationOrder).isEqualTo(AllocationOrder.BY_NAME)
  }

  private fun getPrisonConfig(
    prisonCode: String,
    policy: AllocationPolicy,
  ): PrisonConfigResponse =
    webTestClient
      .get()
      .uri { it.path("/prisons/{prisonCode}/configurations").build(prisonCode) }
      .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(Roles.ALLOCATIONS_UI)))
      .header(PolicyHeader.NAME, policy.name)
      .exchange()
      .expectStatus()
      .isOk
      .expectBody(PrisonConfigResponse::class.java)
      .returnResult()
      .responseBody!!

  companion object {
    @JvmStatic
    fun prisonConfigArgs() =
      listOf(
        Arguments.of(
          "ZEZE",
          AllocationPolicy.KEY_WORKER,
          PrisonConfigResponse(
            false,
            false,
            false,
            9,
            1,
            AllocationOrder.BY_ALLOCATIONS,
          ),
        ),
        Arguments.of(
          "ZEON",
          AllocationPolicy.KEY_WORKER,
          PrisonConfigResponse(false, true, false, 9, 1, AllocationOrder.BY_ALLOCATIONS),
        ),
        Arguments.of(
          "ONZE",
          AllocationPolicy.KEY_WORKER,
          PrisonConfigResponse(true, false, true, 9, 1, AllocationOrder.BY_ALLOCATIONS),
        ),
        Arguments.of(
          "ONON",
          AllocationPolicy.KEY_WORKER,
          PrisonConfigResponse(true, true, true, 9, 1, AllocationOrder.BY_ALLOCATIONS),
        ),
        Arguments.of(
          "ZEZE",
          AllocationPolicy.PERSONAL_OFFICER,
          PrisonConfigResponse(true, true, true, 9, 1, AllocationOrder.BY_ALLOCATIONS),
        ),
        Arguments.of(
          "ZEON",
          AllocationPolicy.PERSONAL_OFFICER,
          PrisonConfigResponse(true, false, true, 9, 1, AllocationOrder.BY_ALLOCATIONS),
        ),
        Arguments.of(
          "ONZE",
          AllocationPolicy.PERSONAL_OFFICER,
          PrisonConfigResponse(false, true, false, 9, 1, AllocationOrder.BY_ALLOCATIONS),
        ),
        Arguments.of(
          "ONON",
          AllocationPolicy.PERSONAL_OFFICER,
          PrisonConfigResponse(false, false, false, 9, 1, AllocationOrder.BY_ALLOCATIONS),
        ),
      )
  }
}
