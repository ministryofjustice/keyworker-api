package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.AfterEach
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
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.JobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.time.LocalDate

class ManageStaffJobClassificationIntTest : IntegrationTest() {
  @AfterEach
  fun resetContext() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
  }

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
  @ValueSource(strings = [Roles.KEYWORKER_RO, Roles.KEYWORKER_RW])
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
  fun `staff config is created if it does not exist`(policy: AllocationPolicy) {
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
  fun `staff config is updated if it exists`(policy: AllocationPolicy) {
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
        hoursPerWeek = 20,
        toDate = LocalDate.now(),
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
  fun `staff config management does not audit no change requests`(policy: AllocationPolicy) {
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

  fun jobClassificationRequest(
    position: String = "PRO",
    scheduleType: String = "FT",
    hoursPerWeek: Int = 40,
    fromDate: LocalDate = LocalDate.now().minusDays(7),
    toDate: LocalDate? = null,
  ) = StaffJobClassificationRequest(position, scheduleType, hoursPerWeek, fromDate, toDate)

  fun manageStaffJobClassification(
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
    const val STAFF_JOB_URL = "/prisons/{prisonCode}/staff/{staffId}/job-classification"
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
