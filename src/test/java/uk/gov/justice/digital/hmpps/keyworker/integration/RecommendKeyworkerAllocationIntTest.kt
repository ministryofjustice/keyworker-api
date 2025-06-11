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
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.RecommendedAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffLocationRoleDto
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaff
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaffMembers
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.sar.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class RecommendKeyworkerAllocationIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_ALLOCATION_RECOMMENDATIONS, "NAU")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getKeyworkerRecommendations("DNM", AllocationPolicy.KEY_WORKER, "ROLE_ANY__OTHER_RO")
      .expectStatus()
      .isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `identifies cases that have no recommendations when all keyworkers are at max capacity`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "FUL"
    givenPrisonConfig(prisonConfig(prisonCode, capacity = 1, policy = policy))
    val prisoners = prisoners(prisonCode, 10)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staff = (0..2).map { staffDetail() }
    nomisUserRolesMockServer.stubGetAllStaff(prisonCode, NomisStaffMembers(nomisStaff(staff)))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staff)
    } else {
      staff.forEach { givenStaffRole(staffRole(prisonCode, it.staffId)) }
    }

    staff.map {
      givenAllocation(staffAllocation(personIdentifier(), prisonCode, it.staffId))
    }

    val res =
      getKeyworkerRecommendations(prisonCode, policy)
        .expectStatus()
        .isOk
        .expectBody(RecommendedAllocations::class.java)
        .returnResult()
        .responseBody!!

    assertThat(res.noAvailableStaffFor).containsExactlyInAnyOrderElementsOf(prisoners.content.map { it.prisonerNumber })
    assertThat(res.allocations).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `will recommend previous keyworker regardless of capacity`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "EXI"
    givenPrisonConfig(prisonConfig(prisonCode, capacity = 1, policy = policy))
    val prisoners = prisoners(prisonCode, 6)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staffWithCapacity = (0..2).map { staffDetail() }
    val staffAtCapacity = (0..2).map { staffDetail() }
    val allStaff = staffWithCapacity + staffAtCapacity
    nomisUserRolesMockServer.stubGetAllStaff(prisonCode, NomisStaffMembers(nomisStaff(allStaff)))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, allStaff)
    } else {
      allStaff.forEach { givenStaffRole(staffRole(prisonCode, it.staffId)) }
    }

    val prisonersReversed = prisoners.content.reversed()
    val previousAllocations =
      staffAtCapacity
        .mapIndexed { i, s ->
          givenAllocation(
            staffAllocation(
              prisonersReversed[i].prisonerNumber,
              prisonCode,
              s.staffId,
              active = false,
              deallocatedAt = LocalDateTime.now().minusDays(1),
              deallocationReason = DeallocationReason.STAFF_STATUS_CHANGE,
              deallocatedBy = "d34ll",
            ),
          )
        }.associateBy { it.staffId }
    staffAtCapacity.map { s ->
      givenAllocation(
        staffAllocation(personIdentifier(), prisonCode, s.staffId),
      )
    }

    val res =
      getKeyworkerRecommendations(prisonCode, policy)
        .expectStatus()
        .isOk
        .expectBody(RecommendedAllocations::class.java)
        .returnResult()
        .responseBody!!

    assertThat(res.noAvailableStaffFor).isEmpty()
    assertThat(res.allocations).containsAll(
      staffAtCapacity.map { s ->
        RecommendedAllocation(
          previousAllocations[s.staffId]!!.personIdentifier,
          StaffSummary(s.staffId, s.firstName, s.lastName),
        )
      },
    )
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `will balance recommendations based on capacity availability and report when keyworkers are at max capacity`(
    policy: AllocationPolicy,
  ) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "BAL"
    givenPrisonConfig(prisonConfig(prisonCode, capacity = 9, policy = policy))
    val prisoners = prisoners(prisonCode, 16)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staff = (0..4).map { staffDetail() }
    staff.map { givenStaffConfig(staffConfig(StaffStatus.ACTIVE, it.staffId, 6)) }
    nomisUserRolesMockServer.stubGetAllStaff(prisonCode, NomisStaffMembers(nomisStaff(staff)))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staff)
    } else {
      staff.forEach { givenStaffRole(staffRole(prisonCode, it.staffId)) }
    }
    staff.mapIndexed { i, s ->
      (1..5 - i).map {
        givenAllocation(
          staffAllocation(personIdentifier(), prisonCode, s.staffId),
        )
      }
    }

    val res =
      getKeyworkerRecommendations(prisonCode, policy)
        .expectStatus()
        .isOk
        .expectBody(RecommendedAllocations::class.java)
        .returnResult()
        .responseBody!!

    val sortedPrisoners = prisoners.content.sortedBy { it.lastName }
    val allocMap = res.allocations.groupBy({ it.staff.staffId }, { it.personIdentifier })
    assertThat(allocMap.toList()).containsExactlyInAnyOrder(
      staff[0].staffId to listOf(sortedPrisoners[14].prisonerNumber),
      staff[1].staffId to listOf(sortedPrisoners[9].prisonerNumber, sortedPrisoners[13].prisonerNumber),
      staff[2].staffId to
        listOf(
          sortedPrisoners[5].prisonerNumber,
          sortedPrisoners[8].prisonerNumber,
          sortedPrisoners[12].prisonerNumber,
        ),
      staff[3].staffId to
        listOf(
          sortedPrisoners[2].prisonerNumber,
          sortedPrisoners[4].prisonerNumber,
          sortedPrisoners[7].prisonerNumber,
          sortedPrisoners[11].prisonerNumber,
        ),
      staff[4].staffId to
        listOf(
          sortedPrisoners[0].prisonerNumber,
          sortedPrisoners[1].prisonerNumber,
          sortedPrisoners[3].prisonerNumber,
          sortedPrisoners[6].prisonerNumber,
          sortedPrisoners[10].prisonerNumber,
        ),
    )
    assertThat(res.noAvailableStaffFor).containsExactly(sortedPrisoners.last().prisonerNumber)
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `will balance recommendations based on capacity availability when capacity is different`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "BAL"
    givenPrisonConfig(prisonConfig(prisonCode, capacity = 9, policy = policy))
    val prisoners = prisoners(prisonCode, 16)
    prisonerSearchMockServer.stubFindFilteredPrisoners(prisonCode, prisoners)

    val staff = (1..2).map { staffDetail() }
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staff[0].staffId, 6))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staff[1].staffId, 12))
    nomisUserRolesMockServer.stubGetAllStaff(prisonCode, NomisStaffMembers(nomisStaff(staff)))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staff)
    } else {
      staff.forEach { givenStaffRole(staffRole(prisonCode, it.staffId)) }
    }

    val res =
      getKeyworkerRecommendations(prisonCode, policy)
        .expectStatus()
        .isOk
        .expectBody(RecommendedAllocations::class.java)
        .returnResult()
        .responseBody!!

    val sortedPrisoners = prisoners.content.sortedBy { it.lastName }
    val allocMap = res.allocations.groupBy({ it.staff.staffId }, { it.personIdentifier })
    assertThat(allocMap.toList()).containsExactlyInAnyOrder(
      staff[0].staffId to
        listOf(
          sortedPrisoners[0].prisonerNumber,
          sortedPrisoners[3].prisonerNumber,
          sortedPrisoners[6].prisonerNumber,
          sortedPrisoners[9].prisonerNumber,
          sortedPrisoners[12].prisonerNumber,
          sortedPrisoners[15].prisonerNumber,
        ),
      staff[1].staffId to
        listOf(
          sortedPrisoners[1].prisonerNumber,
          sortedPrisoners[2].prisonerNumber,
          sortedPrisoners[4].prisonerNumber,
          sortedPrisoners[5].prisonerNumber,
          sortedPrisoners[7].prisonerNumber,
          sortedPrisoners[8].prisonerNumber,
          sortedPrisoners[10].prisonerNumber,
          sortedPrisoners[11].prisonerNumber,
          sortedPrisoners[13].prisonerNumber,
          sortedPrisoners[14].prisonerNumber,
        ),
    )
    assertThat(res.noAvailableStaffFor).isEmpty()
  }

  private fun getKeyworkerRecommendations(
    prisonCode: String,
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .get()
    .uri {
      it.path(GET_ALLOCATION_RECOMMENDATIONS)
      it.build(prisonCode)
    }.headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  private fun prisoners(
    prisonCode: String,
    count: Int,
  ) = Prisoners(
    (1..count)
      .map {
        Prisoner(
          personIdentifier(),
          "First$it",
          "Last$it",
          LocalDate.now().minusWeeks(it.toLong()),
          null,
          prisonCode,
          "Description of $prisonCode",
          "$prisonCode-A-$it",
          "STANDARD",
          null,
          LocalDate.now().minusWeeks(it.toLong()),
        )
      },
  )

  private fun staffDetail(
    id: Long = newId(),
    firstName: String = "First $id",
    lastName: String = "Last $id",
  ): StaffLocationRoleDto =
    StaffLocationRoleDto
      .builder()
      .staffId(id)
      .firstName(firstName)
      .lastName(lastName)
      .position("PRO")
      .scheduleType("FT")
      .hoursPerWeek(BigDecimal(37.5))
      .fromDate(LocalDate.now().minusWeeks(7))
      .build()

  private fun nomisStaff(staff: List<StaffLocationRoleDto>): List<NomisStaff> =
    staff.map {
      NomisStaff(
        "user-${it.staffId}",
        "user-${it.staffId}@email.co.uk",
        it.staffId,
        it.firstName,
        it.lastName,
        "ACTIVE",
      )
    }

  companion object {
    const val GET_ALLOCATION_RECOMMENDATIONS = "/prisons/{prisonCode}/prisoners/allocation-recommendations"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
