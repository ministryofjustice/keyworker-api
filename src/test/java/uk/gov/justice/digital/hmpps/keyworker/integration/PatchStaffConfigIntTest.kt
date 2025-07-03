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
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.ReferenceDataDomain
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.GetStaffDetailsIntegrationTest.Companion.staffDetail
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.math.BigDecimal
import java.time.LocalDate

class PatchStaffConfigIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised`() {
    webTestClient
      .patch()
      .uri(STAFF_CONFIG_URL, "NA1", newId())
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("{}")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @ParameterizedTest
  @ValueSource(strings = [Roles.KEYWORKER_RO])
  @NullSource
  fun `403 forbidden`(role: String?) {
    patchStaffConfig("NA2", newId(), "{}", AllocationPolicy.entries.random(), role = role)
      .expectStatus()
      .isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `404 not found if staff config does not exist`(policy: AllocationPolicy) {
    patchStaffConfig("NA2", newId(), "{}", policy)
      .expectStatus()
      .isNotFound
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `staff config properties are updated if they are defined in the request`(policy: AllocationPolicy) {
    val prisonCode = "UBP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId, capacity = 6, reactivateOn = LocalDate.of(2001, 1, 1)))

    patchStaffConfig(prisonCode, staffId, "{\"capacity\": 100}", policy, username = username)
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

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `job classification properties are updated if they are defined in the request`(policy: AllocationPolicy) {
    val prisonCode = "UBP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))

    val nomisRequest =
      if (policy.nomisUseRoleCode != null) {
        val staffRole = staffDetail(staffId, "PT", "PRO", hoursPerWeek = BigDecimal(20))
        val roleRequest =
          StaffJobClassificationRequest(
            position = staffRole.position,
            scheduleType = staffRole.scheduleType,
            hoursPerWeek = BigDecimal(42),
            fromDate = staffRole.fromDate,
            toDate = staffRole.toDate,
          )
        prisonMockServer.stubKeyworkerDetails(prisonCode, staffId, staffRole)
        nomisUserRolesMockServer.stubSetStaffRole(
          StaffJobClassification(
            prisonCode,
            staffId,
            roleRequest,
          ),
        )
        roleRequest
      } else {
        givenStaffRole(
          staffRole(
            prisonCode,
            staffId,
            scheduleType = withReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE, "PT"),
            hoursPerWeek = BigDecimal(20),
          ),
        )
        null
      }

    patchStaffConfig(prisonCode, staffId, "{\"jobClassification\": {\"hoursPerWeek\": 42}}", policy, username = username)
      .expectStatus()
      .isNoContent

    if (policy.nomisUseRoleCode != null) {
      verify(nomisUserRolesApiClient).setStaffRole(prisonCode, staffId, "KW", nomisRequest!!)
    } else {
      val staffRole = requireNotNull(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, staffId))
      assertThat(staffRole.hoursPerWeek).isEqualTo(BigDecimal(42))
      assertThat(staffRole.scheduleType.key.code).isEqualTo("PT")
      verifyAudit(
        staffRole,
        staffRole.id,
        RevisionType.MOD,
        setOf(StaffRole::class.simpleName!!),
        AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `unset reactivatedOn if null value is submitted`(policy: AllocationPolicy) {
    val prisonCode = "UBP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId, capacity = 6, reactivateOn = LocalDate.of(2001, 1, 1)))

    patchStaffConfig(prisonCode, staffId, "{\"reactivateOn\": null}", policy, username = username)
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

    patchStaffConfig(prisonCode, staffId, "{\"deactivateActiveAllocations\": true}", policy, username = username)
      .expectStatus()
      .isNoContent

    staffAllocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isFalse
      assertThat(it.deallocatedAt?.toLocalDate()).isEqualTo(LocalDate.now())
      assertThat(it.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.reasonCode)
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `Deactivate job role, delete staff config, and deallocate active allocations if jobClassification is null`(policy: AllocationPolicy) {
    val prisonCode = "UBP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))
    var staffRole: StaffRole? = null

    val nomisRequest =
      if (policy.nomisUseRoleCode != null) {
        val staffRole = staffDetail(staffId, "PT", "PRO", hoursPerWeek = BigDecimal(20))
        val roleRequest =
          StaffJobClassificationRequest(
            position = staffRole.position,
            scheduleType = staffRole.scheduleType,
            hoursPerWeek = staffRole.hoursPerWeek,
            fromDate = staffRole.fromDate,
            toDate = LocalDate.now(),
          )
        prisonMockServer.stubKeyworkerDetails(prisonCode, staffId, staffRole)
        nomisUserRolesMockServer.stubSetStaffRole(
          StaffJobClassification(
            prisonCode,
            staffId,
            roleRequest,
          ),
        )
        roleRequest
      } else {
        staffRole =
          givenStaffRole(
            staffRole(
              prisonCode,
              staffId,
              scheduleType = withReferenceData(ReferenceDataDomain.STAFF_SCHEDULE_TYPE, "PT"),
              hoursPerWeek = BigDecimal(20),
            ),
          )
        null
      }
    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    patchStaffConfig(prisonCode, staffId, "{\"jobClassification\": null}", policy, username = username)
      .expectStatus()
      .isNoContent

    if (policy.nomisUseRoleCode != null) {
      verify(nomisUserRolesApiClient).setStaffRole(prisonCode, staffId, "KW", nomisRequest!!)
    } else {
      verifyAudit(
        staffRole!!,
        staffRole.id,
        RevisionType.MOD,
        setOf(StaffRole::class.simpleName!!, Allocation::class.simpleName!!, StaffConfiguration::class.simpleName!!),
        AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
      )
    }

    val staffConfig = staffConfigRepository.findByStaffId(staffId)
    assertThat(staffConfig).isNull()

    staffAllocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isFalse
      assertThat(it.deallocatedAt?.toLocalDate()).isEqualTo(LocalDate.now())
      assertThat(it.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.reasonCode)
    }
  }

  fun patchStaffConfig(
    prisonCode: String,
    staffId: Long,
    request: String,
    policy: AllocationPolicy,
    username: String = TEST_USERNAME,
    caseloadId: String = prisonCode,
    role: String? = Roles.ALLOCATIONS_UI,
  ): WebTestClient.ResponseSpec =
    webTestClient
      .patch()
      .uri(STAFF_CONFIG_URL, prisonCode, staffId)
      .headers(setHeaders(username = username, roles = listOfNotNull(role)))
      .header(CaseloadIdHeader.NAME, caseloadId)
      .header(PolicyHeader.NAME, policy.name)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(request)
      .exchange()

  companion object {
    const val STAFF_CONFIG_URL = "/prisons/{prisonCode}/staff/{staffId}/configuration"
    const val TEST_USERNAME = "T35TUS3R"

    @JvmStatic
    fun policyProvider() =
      listOf(
        Arguments.of(AllocationPolicy.KEY_WORKER),
        Arguments.of(AllocationPolicy.PERSONAL_OFFICER),
      )
  }
}
