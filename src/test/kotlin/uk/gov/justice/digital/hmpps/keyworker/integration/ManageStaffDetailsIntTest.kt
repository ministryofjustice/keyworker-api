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
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffDetailsRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffRoleRequest
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDate.now

class ManageStaffDetailsIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised`() {
    webTestClient
      .put()
      .uri(STAFF_DETAILS_URL, "NA1", newId())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("{}")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @ParameterizedTest
  @ValueSource(strings = ["ROLE_OTHER__RO"])
  @NullSource
  fun `403 forbidden`(role: String?) {
    setStaffDetails("NA2", newId(), staffDetailsRequest(), AllocationPolicy.entries.random(), role = role)
      .expectStatus()
      .isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `create staff config with submitted and fallback values if not already exists`(policy: AllocationPolicy) {
    val prisonCode = "UBP"
    val staffId = newId()
    val username = username()

    if (policy == AllocationPolicy.KEY_WORKER) {
      nomisUserRolesMockServer.stubSetStaffRole(
        StaffJobClassification(
          prisonCode,
          staffId,
          StaffJobClassificationRequest(
            "PRO",
            "FT",
            BigDecimal(37.5),
            now().minusDays(7),
            null,
          ),
        ),
      )
    }

    val request = staffDetailsRequest(capacity = 100)
    setStaffDetails(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    setContext(AllocationContext.get().copy(policy = policy))
    val staffConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))

    assertThat(staffConfig.capacity).isEqualTo(100)
    assertThat(staffConfig.status.code).isEqualTo("ACTIVE")
    assertThat(staffConfig.reactivateOn).isNull()

    verifyAudit(
      staffConfig,
      staffConfig.id,
      RevisionType.ADD,
      if (policy == AllocationPolicy.KEY_WORKER) {
        setOf(StaffConfiguration::class.simpleName!!)
      } else {
        setOf(StaffConfiguration::class.simpleName!!, StaffRole::class.simpleName!!)
      },
      AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
    )
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `create staff role with submitted and fallback values if not already exists`(policy: AllocationPolicy) {
    val prisonCode = "SRC"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))

    val nomisRequest =
      policy.nomisUserRoleCode?.let {
        val roleRequest =
          StaffJobClassificationRequest(
            position = "PRO",
            scheduleType = "FT",
            hoursPerWeek = BigDecimal(40),
            fromDate = now(),
            toDate = null,
          )
        nomisUserRolesMockServer.stubSetStaffRole(
          StaffJobClassification(
            prisonCode,
            staffId,
            roleRequest,
          ),
        )
        roleRequest
      }

    setStaffDetails(
      prisonCode,
      staffId,
      staffDetailsRequest(staffRole = staffRoleRequest(hoursPerWeek = BigDecimal(40), fromDate = now())),
      policy,
      username = username,
    ).expectStatus()
      .isNoContent

    if (policy.nomisUserRoleCode != null) {
      verify(nomisUserRolesApiClient).setStaffRole(prisonCode, staffId, policy.nomisUserRoleCode, nomisRequest!!)
    } else {
      val staffRole = requireNotNull(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId))
      assertThat(staffRole.hoursPerWeek).isEqualTo(BigDecimal(40))
      assertThat(staffRole.scheduleType.code).isEqualTo("FT")
      assertThat(staffRole.position.code).isEqualTo("PRO")
      assertThat(staffRole.fromDate).isEqualTo(now())
      assertThat(staffRole.toDate).isNull()
      verifyAudit(
        staffRole,
        staffRole.id,
        RevisionType.ADD,
        setOf(StaffRole::class.simpleName!!),
        AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `staff config properties are updated if they are defined in the request`(policy: AllocationPolicy) {
    val prisonCode = "SCP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    val sc =
      givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId, capacity = 6, reactivateOn = LocalDate.of(2001, 1, 1)))

    if (policy == AllocationPolicy.KEY_WORKER) {
      nomisUserRolesMockServer.stubSetStaffRole(
        StaffJobClassification(
          prisonCode,
          staffId,
          StaffJobClassificationRequest(
            "PRO",
            "FT",
            BigDecimal(37.5),
            now().minusDays(7),
            null,
          ),
        ),
      )
    } else {
      givenStaffRole(staffRole(prisonCode, staffId))
    }

    val request = staffDetailsRequest(capacity = 100, reactivateOn = sc.reactivateOn)
    setStaffDetails(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    val staffConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))

    assertThat(staffConfig.capacity).isEqualTo(100)
    assertThat(staffConfig.reactivateOn).isEqualTo(LocalDate.of(2001, 1, 1))

    verifyAudit(
      staffConfig,
      staffConfig.id,
      RevisionType.MOD,
      setOf(StaffConfiguration::class.simpleName!!),
      AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
    )
  }

  @Test
  fun `staff role can be reactivated`() {
    val prisonCode = "UDR"
    val staffId = newId()
    val username = username()
    val policy = AllocationPolicy.PERSONAL_OFFICER
    setContext(AllocationContext.get().copy(policy = policy))
    val sc = givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))
    val sr =
      givenStaffRole(
        staffRole(
          prisonCode,
          staffId,
          scheduleType = "PT",
          hoursPerWeek = BigDecimal(20),
          toDate = now().minusDays(1),
        ),
      )
    val role = staffRoleRepository.findRoleIncludingInactiveForPolicy(prisonCode, staffId, policy.name)
    assertThat(role).isNotNull

    val roleRequest = staffRoleRequest(sr.position.code, sr.scheduleType.code, BigDecimal(42), sr.fromDate)
    val request = staffDetailsRequest(sc.status.code, sc.capacity, staffRole = roleRequest)
    setStaffDetails(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    val staffRole =
      requireNotNull(staffRoleRepository.findRoleIncludingInactiveForPolicy(prisonCode, staffId, policy.name))
    assertThat(staffRole.hoursPerWeek).isEqualTo(BigDecimal(42))
    assertThat(staffRole.scheduleType.key.code).isEqualTo("PT")
    assertThat(staffRole.toDate).isNull()
    verifyAudit(
      staffRole,
      staffRole.id,
      RevisionType.MOD,
      setOf(StaffRole::class.simpleName!!),
      AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
    )
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `unset reactivatedOn if null value is submitted`(policy: AllocationPolicy) {
    val prisonCode = "RON"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId, capacity = 6, reactivateOn = LocalDate.of(2001, 1, 1)))

    if (policy == AllocationPolicy.KEY_WORKER) {
      nomisUserRolesMockServer.stubSetStaffRole(
        StaffJobClassification(
          prisonCode,
          staffId,
          StaffJobClassificationRequest(
            "PRO",
            "FT",
            BigDecimal(37.5),
            now().minusDays(7),
            null,
          ),
        ),
      )
    } else {
      givenStaffRole(staffRole(prisonCode, staffId))
    }

    val request = staffDetailsRequest().copy(reactivateOn = null)
    setStaffDetails(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    val staffConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    assertThat(staffConfig.reactivateOn).isNull()

    verifyAudit(
      staffConfig,
      staffConfig.id,
      RevisionType.MOD,
      setOf(StaffConfiguration::class.simpleName!!),
      AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
    )
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `deallocate active allocations if deactivateActiveAllocations is true`(policy: AllocationPolicy) {
    val prisonCode = "UBP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))
    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    if (policy == AllocationPolicy.KEY_WORKER) {
      nomisUserRolesMockServer.stubSetStaffRole(
        StaffJobClassification(
          prisonCode,
          staffId,
          StaffJobClassificationRequest(
            "PRO",
            "FT",
            BigDecimal(37.5),
            now().minusDays(7),
            null,
          ),
        ),
      )
    } else {
      givenStaffRole(staffRole(prisonCode, staffId))
    }

    val request = staffDetailsRequest().copy(deactivateActiveAllocations = true)
    setStaffDetails(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    allocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isFalse
      assertThat(it.deallocatedAt?.toLocalDate()).isEqualTo(now())
      assertThat(it.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.name)
    }
  }

  fun staffRoleRequest(
    position: String = "PRO",
    scheduleType: String = "FT",
    hoursPerWeek: BigDecimal = BigDecimal(37.5),
    fromDate: LocalDate = now().minusDays(7),
    toDate: LocalDate? = null,
  ) = StaffRoleRequest(position, scheduleType, hoursPerWeek, fromDate, toDate)

  fun staffDetailsRequest(
    status: String = "ACTIVE",
    capacity: Int = 6,
    reactivateOn: LocalDate? = null,
    staffRole: StaffRoleRequest = staffRoleRequest(),
    deactivateActiveAllocations: Boolean = false,
  ) = StaffDetailsRequest(status, capacity, reactivateOn, staffRole, deactivateActiveAllocations)

  fun setStaffDetails(
    prisonCode: String,
    staffId: Long,
    request: StaffDetailsRequest,
    policy: AllocationPolicy,
    username: String = TEST_USERNAME,
    caseloadId: String = prisonCode,
    role: String? = Roles.ALLOCATIONS_UI,
  ): WebTestClient.ResponseSpec =
    webTestClient
      .put()
      .uri(STAFF_DETAILS_URL, prisonCode, staffId)
      .headers(setHeaders(username = username, roles = listOfNotNull(role)))
      .header(CaseloadIdHeader.NAME, caseloadId)
      .header(PolicyHeader.NAME, policy.name)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .exchange()

  companion object {
    const val STAFF_DETAILS_URL = "/prisons/{prisonCode}/staff/{staffId}"
    const val TEST_USERNAME = "T35TUS3R"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
