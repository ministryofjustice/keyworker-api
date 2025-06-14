package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.CaseloadIdHeader
import uk.gov.justice.digital.hmpps.keyworker.controllers.Roles
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerConfigRequest
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.time.LocalDate

class ManageKeyworkerConfigIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised`() {
    webTestClient
      .put()
      .uri(KEYWORKER_CONFIG_URL, "NA1", newId())
      .bodyValue(keyworkerConfigRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @ParameterizedTest
  @ValueSource(strings = [Roles.KEYWORKER_RO])
  @NullSource
  fun `403 forbidden`(role: String?) {
    manageKeyworkerConfig("NA2", newId(), keyworkerConfigRequest(), role = role)
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `keyworker config is created if it does not exist`() {
    val prisonCode = "CRE"
    val staffId = newId()
    val request = keyworkerConfigRequest()
    manageKeyworkerConfig(prisonCode, staffId, request, caseloadId = prisonCode)
      .expectStatus()
      .isNoContent

    val kwConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    kwConfig.verifyAgainst(request)
    verifyAudit(
      kwConfig,
      kwConfig.id,
      RevisionType.ADD,
      setOf(StaffConfiguration::class.simpleName!!),
      AllocationContext(username = TEST_USERNAME, activeCaseloadId = prisonCode),
    )
  }

  @Test
  fun `keyworker config is updated if it exists`() {
    val prisonCode = "UPD"
    val staffId = newId()
    val username = username()
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))

    val request =
      keyworkerConfigRequest(
        status = StaffStatus.UNAVAILABLE_ANNUAL_LEAVE,
        capacity = 100,
        removeFromAutoAllocation = true,
        reactivateOn = LocalDate.now().plusDays(7),
      )
    manageKeyworkerConfig(prisonCode, staffId, request, username = username, caseloadId = prisonCode)
      .expectStatus()
      .isNoContent

    val kwConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    kwConfig.verifyAgainst(request)
    verifyAudit(
      kwConfig,
      kwConfig.id,
      RevisionType.MOD,
      setOf(StaffConfiguration::class.simpleName!!),
      AllocationContext(username = username, activeCaseloadId = prisonCode),
    )
  }

  @Test
  fun `keyworker config management does not audit no change requests`() {
    val prisonCode = "NOC"
    val staffId = newId()
    val username = username()
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))
    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    val request = keyworkerConfigRequest(status = StaffStatus.ACTIVE, capacity = 6)
    manageKeyworkerConfig(prisonCode, staffId, request, username = username, caseloadId = prisonCode)
      .expectStatus()
      .isNoContent

    val kwConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    kwConfig.verifyAgainst(request)
    verifyAudit(
      kwConfig,
      kwConfig.id,
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

  @Test
  fun `active allocations are deallocated`() {
    val prisonCode = "DEA"
    val staffId = newId()
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId, capacity = 10))
    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    val request = keyworkerConfigRequest(deactivateActiveAllocations = true)
    manageKeyworkerConfig(prisonCode, staffId, request, caseloadId = prisonCode)
      .expectStatus()
      .isNoContent

    val kwConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    kwConfig.verifyAgainst(request)

    staffAllocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isFalse
      assertThat(it.deallocatedAt?.toLocalDate()).isEqualTo(LocalDate.now())
      assertThat(it.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.reasonCode)
    }
  }

  fun keyworkerConfigRequest(
    status: StaffStatus = StaffStatus.ACTIVE,
    capacity: Int = 10,
    deactivateActiveAllocations: Boolean = false,
    removeFromAutoAllocation: Boolean = false,
    reactivateOn: LocalDate? = null,
  ) = KeyworkerConfigRequest(status, capacity, deactivateActiveAllocations, removeFromAutoAllocation, reactivateOn)

  fun manageKeyworkerConfig(
    prisonCode: String,
    staffId: Long,
    request: KeyworkerConfigRequest,
    username: String = TEST_USERNAME,
    caseloadId: String? = null,
    role: String? = Roles.KEYWORKER_RW,
  ) = webTestClient
    .put()
    .uri(KEYWORKER_CONFIG_URL, prisonCode, staffId)
    .headers { if (caseloadId != null) it.add(CaseloadIdHeader.NAME, caseloadId) }
    .headers(setHeaders(username = username, roles = listOfNotNull(role)))
    .bodyValue(request)
    .exchange()

  companion object {
    const val KEYWORKER_CONFIG_URL = "/prisons/{prisonCode}/keyworkers/{staffId}"
    const val TEST_USERNAME = "T35TUS3R"
  }
}

private fun StaffConfiguration.verifyAgainst(request: KeyworkerConfigRequest) {
  assertThat(status.code).isEqualTo(request.status.name)
  assertThat(capacity).isEqualTo(request.capacity)
  assertThat(allowAutoAllocation).isEqualTo(!request.removeFromAutoAllocation)
  assertThat(reactivateOn).isEqualTo(request.reactivateOn)
}
