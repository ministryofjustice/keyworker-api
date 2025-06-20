package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.verify
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.JobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.math.BigDecimal
import java.time.LocalDate

class ManageStaffJobClassificationIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised`() {
    webTestClient
      .put()
      .uri(STAFF_JOB_URL, "NA1", newId())
      .bodyValue(jobClassificationRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @ParameterizedTest
  @ValueSource(strings = [Roles.KEYWORKER_RO])
  @NullSource
  fun `403 forbidden`(role: String?) {
    manageStaffJobClassification(
      "NA2",
      newId(),
      jobClassificationRequest(),
      AllocationPolicy.entries.random(),
      role = role,
    ).expectStatus().isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `staff classification is created if it does not exist`(policy: AllocationPolicy) {
    val prisonCode = "CSP"
    val staffId = newId()
    val request = jobClassificationRequest()
    stubNomisUserRoles(prisonCode, staffId, request, policy)

    manageStaffJobClassification(prisonCode, staffId, request, policy)
      .expectStatus()
      .isNoContent

    setContext(AllocationContext.get().copy(policy = policy))
    if (policy == AllocationPolicy.PERSONAL_OFFICER) {
      val staffRole = requireNotNull(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId))
      staffRole.verifyAgainst(request)
      verifyAudit(
        staffRole,
        staffRole.id,
        RevisionType.ADD,
        setOf(StaffRole::class.simpleName!!),
        AllocationContext(username = TEST_USERNAME, activeCaseloadId = prisonCode, policy = policy),
      )
    } else {
      verify(nomisUserRolesApiClient).setStaffRole(prisonCode, staffId, "KW", request)
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `staff classification is updated if it exists`(policy: AllocationPolicy) {
    val prisonCode = "USP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    if (policy == AllocationPolicy.PERSONAL_OFFICER) {
      givenStaffRole(staffRole(prisonCode, staffId))
    }

    val request =
      jobClassificationRequest(
        scheduleType = "PT",
        hoursPerWeek = BigDecimal(20),
      )
    stubNomisUserRoles(prisonCode, staffId, request, policy)

    manageStaffJobClassification(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    setContext(AllocationContext.get().copy(policy = policy))
    if (policy == AllocationPolicy.PERSONAL_OFFICER) {
      val staffRole = requireNotNull(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId))
      staffRole.verifyAgainst(request)
      verifyAudit(
        staffRole,
        staffRole.id,
        RevisionType.MOD,
        setOf(StaffRole::class.simpleName!!),
        AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
      )
    } else {
      verify(nomisUserRolesApiClient).setStaffRole(prisonCode, staffId, "KW", request)
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `staff classification does not audit no-change requests`(policy: AllocationPolicy) {
    val prisonCode = "NBP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    if (policy == AllocationPolicy.PERSONAL_OFFICER) {
      givenStaffRole(staffRole(prisonCode, staffId))
    }

    val request = jobClassificationRequest()
    stubNomisUserRoles(prisonCode, staffId, request, policy)
    manageStaffJobClassification(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    setContext(AllocationContext.get().copy(policy = policy))
    if (policy == AllocationPolicy.PERSONAL_OFFICER) {
      val staffRole = requireNotNull(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId))
      staffRole.verifyAgainst(request)
      verifyAudit(
        staffRole,
        staffRole.id,
        RevisionType.ADD,
        setOf(StaffRole::class.simpleName!!),
        AllocationContext(username = "SYS", activeCaseloadId = null),
      )
    } else {
      verify(nomisUserRolesApiClient).setStaffRole(prisonCode, staffId, "KW", request)
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `expiring staff classification removes config and deallocates`(policy: AllocationPolicy) {
    val prisonCode = "ESC"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    val staffRole =
      if (policy == AllocationPolicy.PERSONAL_OFFICER) {
        givenStaffRole(staffRole(prisonCode, staffId))
      } else {
        null
      }

    val allocations =
      (0..5).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    val request =
      jobClassificationRequest(
        scheduleType = staffRole?.scheduleType?.code ?: "PT",
        hoursPerWeek = staffRole?.hoursPerWeek ?: BigDecimal(20),
        toDate = LocalDate.now(),
      )
    stubNomisUserRoles(prisonCode, staffId, request, policy)

    manageStaffJobClassification(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    setContext(AllocationContext.get().copy(policy = policy))
    if (policy == AllocationPolicy.PERSONAL_OFFICER) {
      verifyAudit(
        staffRole!!,
        staffRole.id,
        RevisionType.MOD,
        setOf(StaffRole::class.simpleName!!, Allocation::class.simpleName!!),
        AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
      )
    } else {
      verify(nomisUserRolesApiClient).setStaffRole(prisonCode, staffId, "KW", request)
    }

    val staffConfig = staffConfigRepository.findByStaffId(staffId)
    assertThat(staffConfig).isNull()

    staffAllocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isFalse
      assertThat(it.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.reasonCode)
    }
  }

  private fun jobClassificationRequest(
    position: String = "PRO",
    scheduleType: String = "FT",
    hoursPerWeek: BigDecimal = BigDecimal(37.5),
    fromDate: LocalDate = LocalDate.now().minusDays(7),
    toDate: LocalDate? = null,
  ) = StaffJobClassificationRequest(position, scheduleType, hoursPerWeek, fromDate, toDate)

  private fun manageStaffJobClassification(
    prisonCode: String,
    staffId: Long,
    request: StaffJobClassificationRequest,
    policy: AllocationPolicy,
    username: String = TEST_USERNAME,
    caseloadId: String = prisonCode,
    role: String? = Roles.ALLOCATIONS_UI,
  ): WebTestClient.ResponseSpec =
    webTestClient
      .put()
      .uri(STAFF_JOB_URL, prisonCode, staffId)
      .headers(setHeaders(username = username, roles = listOfNotNull(role)))
      .header(CaseloadIdHeader.NAME, caseloadId)
      .header(PolicyHeader.NAME, policy.name)
      .bodyValue(request)
      .exchange()

  companion object {
    const val STAFF_JOB_URL = "/prisons/{prisonCode}/staff/{staffId}/job-classifications"
    const val TEST_USERNAME = "T35TUS3R"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }

  private fun StaffRole.verifyAgainst(request: JobClassification) {
    assertThat(position.code).isEqualTo(request.position)
    assertThat(scheduleType.code).isEqualTo(request.scheduleType)
    assertThat(hoursPerWeek).isEqualTo(request.hoursPerWeek)
    assertThat(fromDate).isEqualTo(request.fromDate)
    assertThat(toDate).isEqualTo(request.toDate)
  }

  private fun stubNomisUserRoles(
    prisonCode: String,
    staffId: Long,
    request: JobClassification,
    policy: AllocationPolicy,
  ) {
    if (policy == AllocationPolicy.KEY_WORKER) {
      nomisUserRolesMockServer.stubSetStaffRole(StaffJobClassification(prisonCode, staffId, request))
    }
  }
}
