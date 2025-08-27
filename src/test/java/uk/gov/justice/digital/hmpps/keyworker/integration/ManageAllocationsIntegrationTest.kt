package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffDeallocation
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaffMembers
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.fromStaffIds
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.staffRoles
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.staffSummaries
import java.time.LocalDate

class ManageAllocationsIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .put()
      .uri(MANAGE_ALLOCATIONS_URL, "NEP")
      .bodyValue(personStaffAllocations())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    allocateAndDeallocate(
      "DNM",
      personStaffAllocations(),
      AllocationPolicy.PERSONAL_OFFICER,
      "ROLE_ANY__OTHER_RW",
    ).expectStatus().isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `400 bad request - person staff allocations is empty`(policy: AllocationPolicy) {
    val res =
      allocateAndDeallocate("NEN", personStaffAllocations(), policy)
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: At least one allocation or deallocation must be provided")
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `400 bad request - prison not enabled`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val res =
      allocateAndDeallocate("NEN", personStaffAllocations(listOf(personStaffAllocation())), policy)
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: Prison not enabled")
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `400 bad request - prison does not allow auto allocations`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "NCA"
    givenPrisonConfig(prisonConfig(prisonCode, true))
    val res =
      allocateAndDeallocate(
        prisonCode,
        personStaffAllocations(listOf(personStaffAllocation(allocationReason = AllocationReason.AUTO.name))),
        policy,
      ).expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: Prison does not allow auto-allocation")
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `400 bad request - prisoner not at provided prison`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "PRM"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))
    val psa = personStaffAllocation()
    prisonerSearchMockServer.stubFindPrisonerDetails(
      prisonCode,
      setOf(psa.personIdentifier),
      listOf(prisoner(psa.personIdentifier)),
    )

    val res =
      allocateAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)), policy)
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: A provided person identifier is not currently at the provided prison")
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `400 bad request - staff id not allocatable at provided prison`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "SNK"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoner = prisoner(prisonCode)
    val staffId = newId()
    val psa = personStaffAllocation(prisoner.prisonerNumber, staffId)
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, setOf(psa.personIdentifier), listOf(prisoner))
    nomisUserRolesMockServer.stubGetUserStaff(prisonCode, NomisStaffMembers(fromStaffIds(listOf(staffId))))
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf()))

    val res =
      allocateAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)), policy)
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: A provided staff id is not allocatable for the provided prison")
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `400 bad request - staff id not an active staff member`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "SIK"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoner = prisoner(prisonCode)
    val staffId = newId()
    val psa = personStaffAllocation(prisoner.prisonerNumber, staffId)
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, setOf(psa.personIdentifier), listOf(prisoner))
    prisonMockServer.stubStaffSummaries(staffSummaries(setOf(staffId)))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    } else {
      givenStaffRole(staffRole(prisonCode, staffId))
    }

    givenStaffConfig(staffConfig(StaffStatus.INACTIVE, staffId))

    val res =
      allocateAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)), policy)
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: A provided staff id is not configured correctly for the allocation reason")
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `204 no content - new allocations`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "NAL"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staffId = newId()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staffId) }
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubStaffSummaries(staffSummaries(setOf(staffId)))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    } else {
      givenStaffRole(staffRole(prisonCode, staffId))
    }

    allocateAndDeallocate(prisonCode, personStaffAllocations(psas), policy).expectStatus().isNoContent

    val allocations = allocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }
    allocations.forEach { a ->
      verifyAudit(
        a,
        a.id,
        RevisionType.ADD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "keyworker-ui", policy = policy),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `204 no content - new allocations and existing allocations deallocated`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "EAL"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staffId = newId()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staffId) }
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubStaffSummaries(staffSummaries(setOf(staffId)))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    } else {
      givenStaffRole(staffRole(prisonCode, staffId))
    }
    val existingAllocations =
      prisoners.map { givenAllocation(staffAllocation(it.prisonerNumber, prisonCode)) }

    allocateAndDeallocate(prisonCode, personStaffAllocations(psas), policy).expectStatus().isNoContent

    val allocations = allocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }

    allocationRepository.findAllById(existingAllocations.map { it.id }).forEach { allocation ->
      assertThat(allocation.isActive).isFalse
      assertThat(allocation.deallocatedAt).isNotNull
      assertThat(allocation.deallocationReason?.code).isEqualTo(DeallocationReason.OVERRIDE.reasonCode)
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.MOD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "keyworker-ui", policy = policy),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `204 no content - no changes if already allocated to same staff`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "NNA"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staffId = newId()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staffId) }
    prisonerSearchMockServer.stubFindPrisonerDetails(prisonCode, prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubStaffSummaries(staffSummaries(setOf(staffId)))
    if (policy == AllocationPolicy.KEY_WORKER) {
      prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    } else {
      givenStaffRole(staffRole(prisonCode, staffId))
    }
    val existingAllocations =
      prisoners.map { givenAllocation(staffAllocation(it.prisonerNumber, prisonCode, staffId)) }

    allocateAndDeallocate(prisonCode, personStaffAllocations(psas), policy).expectStatus().isNoContent

    val allocations = allocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }

    allocationRepository.findAllById(existingAllocations.map { it.id }).forEach { allocation ->
      assertThat(allocation.isActive).isTrue
      assertThat(allocation.deallocatedAt).isNull()
      assertThat(allocation.deallocationReason?.code).isNull()
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.ADD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "SYS", policy = policy),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `204 no content - deallocate existing allocations`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "DAL"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val staffId = newId()
    val psds = listOf(personStaffDeallocation(staffId = staffId), personStaffDeallocation(staffId = staffId))
    val existing = psds.map { givenAllocation(staffAllocation(it.personIdentifier, prisonCode, staffId)) }

    allocateAndDeallocate(prisonCode, personStaffAllocations(deallocations = psds), policy).expectStatus().isNoContent

    val allocations = allocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations.isEmpty()).isTrue

    allocationRepository.findAllById(existing.map { it.id }).forEach { allocation ->
      assertThat(allocation.isActive).isFalse
      assertThat(allocation.deallocatedAt).isNotNull
      assertThat(allocation.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.reasonCode)
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.MOD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "keyworker-ui", policy = policy),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `204 no content - doesn't deallocate unless person and staff match`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(policy = policy))
    val prisonCode = "DDA"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val staffId = newId()
    val psds = listOf(personStaffDeallocation(), personStaffDeallocation())
    val existing = psds.map { givenAllocation(staffAllocation(it.personIdentifier, prisonCode, staffId)) }

    allocateAndDeallocate(prisonCode, personStaffAllocations(deallocations = psds), policy).expectStatus().isNoContent

    val allocations = allocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations.isEmpty()).isFalse

    allocationRepository.findAllById(existing.map { it.id }).forEach { allocation ->
      assertThat(allocation.isActive).isTrue
      assertThat(allocation.deallocatedAt).isNull()
      assertThat(allocation.deallocationReason?.code).isNull()
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.ADD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "SYS", policy = policy),
      )
    }
  }

  private fun prisoner(
    prisonCode: String,
    personIdentifier: String = personIdentifier(),
  ): Prisoner =
    Prisoner(
      personIdentifier,
      "First",
      "Last",
      LocalDate.now().minusDays(10),
      null,
      prisonCode,
      prisonCode,
      "Description of $prisonCode",
      null,
      null,
      ComplexityOfNeedLevel.LOW,
      LocalDate.now().minusDays(10),
      listOf(),
    )

  private fun personStaffAllocation(
    personIdentifier: String = personIdentifier(),
    staffId: Long = newId(),
    allocationReason: String = AllocationReason.MANUAL.name,
  ) = PersonStaffAllocation(personIdentifier, staffId, allocationReason)

  private fun personStaffDeallocation(
    personIdentifier: String = personIdentifier(),
    staffId: Long = newId(),
    deallocationReason: String = DeallocationReason.STAFF_STATUS_CHANGE.name,
  ) = PersonStaffDeallocation(personIdentifier, staffId, deallocationReason)

  private fun personStaffAllocations(
    allocations: List<PersonStaffAllocation> = emptyList(),
    deallocations: List<PersonStaffDeallocation> = emptyList(),
  ) = PersonStaffAllocations(allocations, deallocations)

  private fun allocateAndDeallocate(
    prisonCode: String,
    request: PersonStaffAllocations,
    policy: AllocationPolicy,
    role: String? = Roles.ALLOCATIONS_UI,
  ) = webTestClient
    .put()
    .uri(MANAGE_ALLOCATIONS_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .header(PolicyHeader.NAME, policy.name)
    .exchange()

  companion object {
    const val MANAGE_ALLOCATIONS_URL = "/prisons/{prisonCode}/prisoners/allocations"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
