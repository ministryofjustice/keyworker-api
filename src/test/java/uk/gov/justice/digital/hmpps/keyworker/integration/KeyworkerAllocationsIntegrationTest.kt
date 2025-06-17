package uk.gov.justice.digital.hmpps.keyworker.integration

import io.jsonwebtoken.security.Jwks.OP.policy
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.dto.ErrorResponse
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocation
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffAllocations
import uk.gov.justice.digital.hmpps.keyworker.dto.PersonStaffDeallocation
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffSummary
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisStaffMembers
import uk.gov.justice.digital.hmpps.keyworker.model.AllocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.fromStaffIds
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.staffRoles
import java.time.LocalDate

class KeyworkerAllocationsIntegrationTest : IntegrationTest() {
  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .put()
      .uri(KEYWORKER_ALLOCATIONS_URL, "NEP")
      .bodyValue(personStaffAllocations())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    allocationAndDeallocate("DNM", personStaffAllocations(), "ROLE_ANY__OTHER_RW").expectStatus().isForbidden
  }

  @Test
  fun `400 bad request - person staff allocations is empty`() {
    val res =
      allocationAndDeallocate("NEN", personStaffAllocations())
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: At least one allocation or deallocation must be provided")
  }

  @Test
  fun `400 bad request - prison not enabled`() {
    val res =
      allocationAndDeallocate("NEN", personStaffAllocations(listOf(personStaffAllocation())))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: Prison not enabled")
  }

  @Test
  fun `400 bad request - prisoner not at provided prison`() {
    val prisonCode = "PRM"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))
    val psa = personStaffAllocation()
    prisonerSearchMockServer.stubFindPrisonDetails(
      prisonCode,
      setOf(psa.personIdentifier),
      listOf(prisoner(psa.personIdentifier)),
    )

    val res =
      allocationAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: A provided person identifier is not currently at the provided prison")
  }

  @Test
  fun `400 bad request - staff id not a keyworker at provided prison`() {
    val prisonCode = "SNK"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoner = prisoner(prisonCode)
    val staffId = newId()
    val psa = personStaffAllocation(prisoner.prisonerNumber, staffId)
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, setOf(psa.personIdentifier), listOf(prisoner))
    nomisUserRolesMockServer.stubGetUserStaff(prisonCode, NomisStaffMembers(fromStaffIds(listOf(staffId))))
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf()))

    val res =
      allocationAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: A provided staff id is not allocatable for the provided prison")
  }

  @Test
  fun `400 bad request - staff id not an active keyworker`() {
    val prisonCode = "SIK"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoner = prisoner(prisonCode)
    val staffId = newId()
    val psa = personStaffAllocation(prisoner.prisonerNumber, staffId)
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, setOf(psa.personIdentifier), listOf(prisoner))
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    prisonMockServer.stubStaffSummaries(listOf(StaffSummary(staffId, "First$staffId", "Last$staffId")))

    givenStaffConfig(staffConfig(StaffStatus.INACTIVE, staffId))

    val res =
      allocationAndDeallocate(prisonCode, personStaffAllocations(listOf(psa)))
        .expectStatus()
        .isBadRequest
        .expectBody(ErrorResponse::class.java)
        .returnResult()
        .responseBody!!
    assertThat(res.userMessage).isEqualTo("Validation failure: A provided staff id is not configured correctly for the allocation reason")
  }

  @Test
  fun `204 no content - new allocations`() {
    val prisonCode = "NAL"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staffId = newId()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staffId) }
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    prisonMockServer.stubStaffSummaries(listOf(StaffSummary(staffId, "First$staffId", "Last$staffId")))

    allocationAndDeallocate(prisonCode, personStaffAllocations(psas)).expectStatus().isNoContent

    val allocations = staffAllocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }
    allocations.forEach { allocation ->
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.ADD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "keyworker-ui", policy = AllocationPolicy.KEY_WORKER),
      )
    }
  }

  @Test
  fun `204 no content - new allocations and existing allocations deallocated`() {
    val prisonCode = "EAL"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staffId = newId()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staffId) }
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    prisonMockServer.stubStaffSummaries(listOf(StaffSummary(staffId, "First$staffId", "Last$staffId")))
    val existingAllocations =
      prisoners.map { givenAllocation(staffAllocation(it.prisonerNumber, prisonCode)) }

    allocationAndDeallocate(prisonCode, personStaffAllocations(psas)).expectStatus().isNoContent

    val allocations = staffAllocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }

    staffAllocationRepository.findAllById(existingAllocations.map { it.id }).forEach { allocation ->
      assertThat(allocation.isActive).isFalse
      assertThat(allocation.deallocatedAt).isNotNull
      assertThat(allocation.deallocationReason?.code).isEqualTo(DeallocationReason.OVERRIDE.reasonCode)
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.MOD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "keyworker-ui", policy = AllocationPolicy.KEY_WORKER),
      )
    }
  }

  @Test
  fun `204 no content - no changes if already allocated to same staff`() {
    val prisonCode = "NNA"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val prisoners = listOf(prisoner(prisonCode))
    val staffId = newId()
    val psas = prisoners.map { personStaffAllocation(it.prisonerNumber, staffId) }
    prisonerSearchMockServer.stubFindPrisonDetails(prisonCode, prisoners.map { it.prisonerNumber }.toSet(), prisoners)
    prisonMockServer.stubKeyworkerSearch(prisonCode, staffRoles(listOf(staffId)))
    prisonMockServer.stubStaffSummaries(listOf(StaffSummary(staffId, "First$staffId", "Last$staffId")))
    val existingAllocations =
      prisoners.map { givenAllocation(staffAllocation(it.prisonerNumber, prisonCode, staffId)) }

    allocationAndDeallocate(prisonCode, personStaffAllocations(psas)).expectStatus().isNoContent

    val allocations = staffAllocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations).hasSize(psas.size)
    psas.forEach { psa ->
      assertThat(allocations.firstOrNull { it.personIdentifier == psa.personIdentifier && it.staffId == psa.staffId }).isNotNull
    }

    staffAllocationRepository.findAllById(existingAllocations.map { it.id }).forEach { allocation ->
      assertThat(allocation.isActive).isTrue
      assertThat(allocation.deallocatedAt).isNull()
      assertThat(allocation.deallocationReason?.code).isNull()
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.ADD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "SYS", policy = AllocationPolicy.KEY_WORKER),
      )
    }
  }

  @Test
  fun `204 no content - deallocate existing allocations`() {
    val prisonCode = "DAL"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val staffId = newId()
    val psds = listOf(personStaffDeallocation(staffId = staffId), personStaffDeallocation(staffId = staffId))
    val existing = psds.map { givenAllocation(staffAllocation(it.personIdentifier, prisonCode, staffId)) }

    allocationAndDeallocate(prisonCode, personStaffAllocations(deallocations = psds)).expectStatus().isNoContent

    val allocations = staffAllocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations.isEmpty()).isTrue

    staffAllocationRepository.findAllById(existing.map { it.id }).forEach { allocation ->
      assertThat(allocation.isActive).isFalse
      assertThat(allocation.deallocatedAt).isNotNull
      assertThat(allocation.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.reasonCode)
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.MOD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "keyworker-ui", policy = AllocationPolicy.KEY_WORKER),
      )
    }
  }

  @Test
  fun `204 no content - doesn't deallocate unless person and staff match`() {
    val prisonCode = "DDA"
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true))

    val staffId = newId()
    val psds = listOf(personStaffDeallocation(), personStaffDeallocation())
    val existing = psds.map { givenAllocation(staffAllocation(it.personIdentifier, prisonCode, staffId)) }

    allocationAndDeallocate(prisonCode, personStaffAllocations(deallocations = psds)).expectStatus().isNoContent

    val allocations = staffAllocationRepository.findActiveForPrisonStaff(prisonCode, staffId)
    assertThat(allocations.isEmpty()).isFalse

    staffAllocationRepository.findAllById(existing.map { it.id }).forEach { allocation ->
      assertThat(allocation.isActive).isTrue
      assertThat(allocation.deallocatedAt).isNull()
      assertThat(allocation.deallocationReason?.code).isNull()
      verifyAudit(
        allocation,
        allocation.id,
        RevisionType.ADD,
        setOf(Allocation::class.simpleName!!),
        AllocationContext.get().copy(username = "SYS", policy = AllocationPolicy.KEY_WORKER),
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
      "Description of $prisonCode",
      null,
      null,
      ComplexityOfNeedLevel.LOW,
      LocalDate.now().minusDays(10),
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

  private fun allocationAndDeallocate(
    prisonCode: String,
    request: PersonStaffAllocations,
    role: String? = Roles.KEYWORKER_RW,
  ) = webTestClient
    .put()
    .uri(KEYWORKER_ALLOCATIONS_URL, prisonCode)
    .bodyValue(request)
    .headers(setHeaders(username = "keyworker-ui", roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val KEYWORKER_ALLOCATIONS_URL = "/prisons/{prisonCode}/prisoners/keyworkers"
  }
}
