package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.verify
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.Allocation
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassification
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.nomisStaffRole
import java.math.BigDecimal
import java.time.LocalDate.now

class RemoveStaffRoleIntTest : IntegrationTest() {
  @ParameterizedTest
  @MethodSource("policyProvider")
  fun `Deactivate job role, delete staff config, and deallocate active allocations`(policy: AllocationPolicy) {
    val prisonCode = "RSR"
    val staffId = newId()
    val username = username()
    setContext(AllocationContext.get().copy(policy = policy))
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))

    val nomisRequest =
      if (policy.nomisUserRoleCode != null) {
        val staffRole = nomisStaffRole(staffId, scheduleType = "PT", position = "PRO", hoursPerWeek = BigDecimal(20))
        val roleRequest =
          StaffJobClassificationRequest(
            position = staffRole.position,
            scheduleType = staffRole.scheduleType,
            hoursPerWeek = staffRole.hoursPerWeek,
            fromDate = staffRole.fromDate,
            toDate = now(),
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
        givenStaffRole(staffRole(prisonCode, staffId, scheduleType = "PT", hoursPerWeek = BigDecimal(20)))
        null
      }
    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    removeStaffRole(prisonCode, staffId, policy, username = username).expectStatus().isNoContent

    if (policy.nomisUserRoleCode != null) {
      verify(nomisUserRolesApiClient).setStaffRole(prisonCode, staffId, "KW", nomisRequest!!)
    } else {
      val staffRole =
        requireNotNull(staffRoleRepository.findRoleIncludingInactiveForPolicy(prisonCode, staffId, policy.name))
      assertThat(staffRole.toDate).isEqualTo(now())
      verifyAudit(
        staffRole,
        staffRole.id,
        RevisionType.MOD,
        setOf(StaffRole::class.simpleName!!, Allocation::class.simpleName!!, StaffConfiguration::class.simpleName!!),
        AllocationContext(username = username, activeCaseloadId = prisonCode, policy = policy),
      )
    }

    val staffConfig = staffConfigRepository.findByStaffId(staffId)
    assertThat(staffConfig).isNull()

    allocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isFalse
      assertThat(it.deallocatedAt?.toLocalDate()).isEqualTo(now())
      assertThat(it.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.name)
    }
  }

  fun removeStaffRole(
    prisonCode: String,
    staffId: Long,
    policy: AllocationPolicy,
    username: String = TEST_USERNAME,
    caseloadId: String = prisonCode,
    role: String? = Roles.ALLOCATIONS_UI,
  ): WebTestClient.ResponseSpec =
    webTestClient
      .delete()
      .uri(STAFF_DETAILS_URL, prisonCode, staffId)
      .headers(setHeaders(username = username, roles = listOfNotNull(role)))
      .header(CaseloadIdHeader.NAME, caseloadId)
      .header(PolicyHeader.NAME, policy.name)
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
