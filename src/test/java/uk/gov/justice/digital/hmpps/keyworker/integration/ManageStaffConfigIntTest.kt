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
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.StaffConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.time.LocalDate

class ManageStaffConfigIntTest : IntegrationTest() {
  @AfterEach
  fun resetContext() {
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
  }

  @Test
  fun `401 unauthorised`() {
    webTestClient
      .put()
      .uri(STAFF_CONFIG_URL, "NA1", newId())
      .bodyValue(staffConfigRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @ParameterizedTest
  @ValueSource(strings = [Roles.KEYWORKER_RO, Roles.KEYWORKER_RW])
  @NullSource
  fun `403 forbidden`(role: String?) {
    manageStaffConfig("NA2", newId(), staffConfigRequest(), AllocationPolicy.entries.random(), role = role)
      .expectStatus()
      .isForbidden
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `staff config is created if it does not exist`(policy: AllocationPolicy) {
    val prisonCode = "CBP"
    val staffId = newId()
    val request = staffConfigRequest()

    manageStaffConfig(prisonCode, staffId, request, policy)
      .expectStatus()
      .isNoContent

    setContext(AllocationContext.get().copy(policy = policy))
    val staffConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    staffConfig.verifyAgainst(request)

    verifyAudit(
      staffConfig,
      staffConfig.id,
      RevisionType.ADD,
      setOf(StaffConfiguration::class.simpleName!!),
      AllocationContext(username = TEST_USERNAME, activeCaseloadId = prisonCode, policy = policy),
    )
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `staff config is updated if it exists`(policy: AllocationPolicy) {
    val prisonCode = "UBP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))

    val request =
      staffConfigRequest(
        status = StaffStatus.UNAVAILABLE_ANNUAL_LEAVE,
        capacity = 100,
        removeFromAutoAllocation = true,
        reactivateOn = LocalDate.now().plusDays(7),
      )
    manageStaffConfig(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    val staffConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    staffConfig.verifyAgainst(request)
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
  fun `staff config management does not audit no change requests`(policy: AllocationPolicy) {
    val prisonCode = "NBP"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))

    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    val request = staffConfigRequest(status = StaffStatus.ACTIVE, capacity = 6)
    manageStaffConfig(prisonCode, staffId, request, policy, username = username)
      .expectStatus()
      .isNoContent

    val staffConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    staffConfig.verifyAgainst(request)
    verifyAudit(
      staffConfig,
      staffConfig.id,
      RevisionType.ADD,
      setOf(StaffConfiguration::class.simpleName!!),
      AllocationContext(username = "SYS", activeCaseloadId = null),
    )
    staffAllocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isTrue
      assertThat(it.deallocatedAt).isNull()
      assertThat(it.deallocationReason).isNull()
    }
  }

  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `active allocations are deallocated`(policy: AllocationPolicy) {
    val prisonCode = "DBP"
    val staffId = newId()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId, capacity = 10))
    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    val request = staffConfigRequest(deactivateActiveAllocations = true)
    manageStaffConfig(prisonCode, staffId, request, policy)
      .expectStatus()
      .isNoContent

    val staffConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    staffConfig.verifyAgainst(request)

    staffAllocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isFalse
      assertThat(it.deallocatedAt?.toLocalDate()).isEqualTo(LocalDate.now())
      assertThat(it.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.reasonCode)
    }
  }

  fun staffConfigRequest(
    status: StaffStatus = StaffStatus.ACTIVE,
    capacity: Int = 10,
    deactivateActiveAllocations: Boolean = false,
    removeFromAutoAllocation: Boolean = false,
    reactivateOn: LocalDate? = null,
  ) = StaffConfigRequest(
    status,
    capacity,
    deactivateActiveAllocations,
    removeFromAutoAllocation,
    reactivateOn,
  )

  fun manageStaffConfig(
    prisonCode: String,
    staffId: Long,
    request: StaffConfigRequest,
    policy: AllocationPolicy,
    username: String = TEST_USERNAME,
    caseloadId: String = prisonCode,
    role: String? = Roles.ALLOCATIONS_UI,
  ): WebTestClient.ResponseSpec =
    webTestClient
      .put()
      .uri(STAFF_CONFIG_URL, prisonCode, staffId)
      .headers(setHeaders(username = username, roles = listOfNotNull(role)))
      .header(CaseloadIdHeader.NAME, caseloadId)
      .header(PolicyHeader.NAME, policy.name)
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

  private fun StaffConfiguration.verifyAgainst(request: StaffConfigRequest) {
    assertThat(status.code).isEqualTo(request.status.name)
    assertThat(capacity).isEqualTo(request.capacity)
    assertThat(allowAutoAllocation).isEqualTo(!request.removeFromAutoAllocation)
    assertThat(reactivateOn).isEqualTo(request.reactivateOn)
  }
}
