package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.integration.events.offender.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.PrisonAlert
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoner
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonersearch.Prisoners
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.person.PersonSearchRequest
import uk.gov.justice.digital.hmpps.keyworker.model.person.PersonSearchResponse
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.time.LocalDate
import java.time.LocalDateTime

class PersonSearchIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .post()
      .uri(SEARCH_URL, "NEP")
      .bodyValue(searchRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    searchPersonSpec(
      "DNM",
      searchRequest(),
      AllocationPolicy.PERSONAL_OFFICER,
      "ROLE_ANY__OTHER_RW",
    ).expectStatus().isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can filter people and decorate with staff member`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "FIND"
    givenPrisonConfig(prisonConfig(prisonCode))

    val prisoners = prisoners(prisonCode, 10)
    prisonerSearchMockServer.stubFindFilteredPrisoners(
      prisonCode,
      prisoners,
      mapOf("cellLocationPrefix" to "$prisonCode-A"),
    )

    val staffIds = (0..6).map { newId() }
    val staffCountMap = staffIds.associateWith { 0 }.toMutableMap()
    val allocations =
      prisoners.content.mapIndexedNotNull { index, p ->
        if (index == 0) {
          null
        } else {
          val activeAllocation = index % 3 != 0
          val staffId = staffIds.random()
          if (activeAllocation) {
            staffCountMap[staffId] = staffCountMap[staffId]!! + 1
          }
          givenAllocation(
            staffAllocation(
              p.prisonerNumber,
              prisonCode,
              staffId,
              active = activeAllocation,
              deallocatedAt = if (activeAllocation) null else LocalDateTime.now().minusDays(index.toLong()),
              deallocatedBy = if (activeAllocation) null else "DA$index",
              deallocationReason = if (activeAllocation) null else DeallocationReason.TRANSFER,
            ),
          )
        }
      }

    val summaries =
      allocations
        .filter { it.isActive }
        .map { it.staffId }
        .distinct()
        .map { StaffSummary(it, "Allocation$it", "Staff$it") }
    prisonMockServer.stubStaffSummaries(summaries)

    val response =
      searchPersonSpec(prisonCode, searchRequest(cellLocationPrefix = "$prisonCode-A"), policy)
        .expectStatus()
        .isOk
        .expectBody(PersonSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content).hasSize(10)
    val none = requireNotNull(response.content.find { it.location == "$prisonCode-A-1" })
    with(none) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isFalse
      assertThat(staffMember).isNull()
    }
    val history = requireNotNull(response.content.find { it.location == "$prisonCode-A-4" })
    with(history) {
      assertThat(hasHighComplexityOfNeeds).isTrue
      assertThat(hasAllocationHistory).isTrue
      assertThat(staffMember).isNull()
    }
    val active = requireNotNull(response.content.find { it.location == "$prisonCode-A-3" })
    with(active) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isTrue
      assertThat(staffMember).isNotNull
    }
    assertThat(response.content.first().relevantAlertCodes).containsExactlyInAnyOrder("RNO121")
    assertThat(response.content.first().remainingAlertCount).isEqualTo(1)

    response.content.forEach { ps ->
      ps.staffMember?.also {
        assertThat(it.allocationCount).isEqualTo(staffCountMap[it.staffId])
      }
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can filter complex needs people and decorate with staff member`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "COMP"
    givenPrisonConfig(prisonConfig(prisonCode, hasPrisonersWithHighComplexityNeeds = true))

    val prisoners = prisoners(prisonCode, 6)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners, mapOf("term" to "First"))

    val staffIds = (0..6).map { newId() }

    val allocations =
      prisoners.content.mapIndexedNotNull { index, p ->
        if (index == 0) {
          null
        } else {
          val activeAllocation = index % 3 != 0
          givenAllocation(
            staffAllocation(
              p.prisonerNumber,
              prisonCode,
              staffIds.random(),
              active = activeAllocation,
              deallocatedAt = if (activeAllocation) null else LocalDateTime.now().minusDays(index.toLong()),
              deallocatedBy = if (activeAllocation) null else "DA$index",
              deallocationReason = if (activeAllocation) null else DeallocationReason.TRANSFER,
            ),
          )
        }
      }

    val summaries =
      allocations
        .filter { it.isActive }
        .map { it.staffId }
        .distinct()
        .map { StaffSummary(it, "Allocation$it", "Staff$it") }
    prisonMockServer.stubStaffSummaries(summaries)

    val response =
      searchPersonSpec(prisonCode, searchRequest(query = "First"), policy)
        .expectStatus()
        .isOk
        .expectBody(PersonSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content).hasSize(6)
    val none = requireNotNull(response.content.find { it.firstName == "First1" })
    with(none) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isFalse
      assertThat(staffMember).isNull()
    }
    val history = requireNotNull(response.content.find { it.firstName == "First4" })
    with(history) {
      assertThat(hasHighComplexityOfNeeds).isTrue
      assertThat(hasAllocationHistory).isTrue
      assertThat(staffMember).isNull()
    }
    val active = requireNotNull(response.content.find { it.firstName == "First3" })
    with(active) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isTrue
      assertThat(staffMember).isNotNull
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `can exclude active and decorate with staff member`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "EXAC"
    givenPrisonConfig(prisonConfig(prisonCode))

    val prisoners = prisoners(prisonCode, 10)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staffIds = (0..6).map { newId() }
    val summaries = staffIds.map { StaffSummary(it, "Allocation$it", "Staff$it") }
    prisonMockServer.stubStaffSummaries(summaries)

    prisoners.content.mapIndexed { index, p ->
      if (index == 0) {
        null
      } else {
        val activeAllocation = index % 3 != 0
        givenAllocation(
          staffAllocation(
            p.prisonerNumber,
            prisonCode,
            staffIds.random(),
            active = activeAllocation,
            deallocatedAt = if (activeAllocation) null else LocalDateTime.now().minusDays(index.toLong()),
            deallocatedBy = if (activeAllocation) null else "DA$index",
            deallocationReason = if (activeAllocation) null else DeallocationReason.TRANSFER,
          ),
        )
      }
    }

    val response =
      searchPersonSpec(prisonCode, searchRequest(excludeActiveAllocations = true), policy)
        .expectStatus()
        .isOk
        .expectBody(PersonSearchResponse::class.java)
        .returnResult()
        .responseBody!!

    assertThat(response.content.filter { it.staffMember != null }).isEmpty()
    val none = requireNotNull(response.content.find { it.location == "$prisonCode-A-1" })
    with(none) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isFalse
      assertThat(staffMember).isNull()
    }
    val complex = response.content.find { it.location == "$prisonCode-A-4" }
    assertThat(complex).isNull()
    val history = requireNotNull(response.content.find { it.location == "$prisonCode-A-7" })
    with(history) {
      assertThat(hasHighComplexityOfNeeds).isFalse
      assertThat(hasAllocationHistory).isTrue
      assertThat(staffMember).isNull()
    }
  }

  private fun searchRequest(
    query: String? = null,
    cellLocationPrefix: String? = null,
    excludeActiveAllocations: Boolean = false,
  ) = PersonSearchRequest(query, cellLocationPrefix, excludeActiveAllocations)

  private fun searchPersonSpec(
    prisonCode: String,
    request: PersonSearchRequest,
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .post()
    .uri(SEARCH_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  private fun prisoners(
    prisonCode: String,
    count: Int,
  ): Prisoners {
    val nonHigh = listOf(ComplexityOfNeedLevel.LOW, ComplexityOfNeedLevel.MEDIUM)
    return Prisoners(
      (1..count).map {
        Prisoner(
          personIdentifier(),
          "First$it",
          "Last$it",
          LocalDate.now().minusWeeks(it.toLong()),
          null,
          prisonCode,
          prisonCode,
          "Description of $prisonCode",
          "$prisonCode-A-$it",
          "STANDARD",
          when {
            it % 3 == 0 -> null
            it % 2 == 0 -> ComplexityOfNeedLevel.HIGH
            else -> nonHigh.random()
          },
          null,
          listOf(
            PrisonAlert("Type", "XRF", false, true),
            PrisonAlert("Type", "RNO121", true, false),
            PrisonAlert("Type", "OTHER1", true, false),
          ),
        )
      },
    )
  }

  companion object {
    const val SEARCH_URL = "/search/prisons/{prisonCode}/prisoners"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
