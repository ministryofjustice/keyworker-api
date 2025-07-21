package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerStatusBehaviour
import uk.gov.justice.digital.hmpps.keyworker.dto.KeyworkerUpdateDto
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.model.LegacyKeyworkerConfiguration
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.username
import java.time.LocalDate

class ManageLegacyKeyworkerConfigIntTest : IntegrationTest() {
  @Test
  fun `401 unauthorised`() {
    webTestClient
      .get()
      .uri(LEGACY_KEYWORKER_CONFIG_URL, newId(), "NA1")
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @ParameterizedTest
  @ValueSource(strings = ["ROLE_OTHER__RO"])
  @NullSource
  fun `403 forbidden`(role: String?) {
    manageKeyworkerConfig("NA2", newId(), keyworkerConfigRequest(), role = role)
      .expectStatus()
      .isForbidden
  }

  @Test
  fun `keyworker config is created if it does not exist`() {
    val prisonCode = "LE1"
    givenPrisonConfig(prisonConfig(prisonCode, true, true))
    val staffId = newId()
    val request = keyworkerConfigRequest()
    manageKeyworkerConfig(prisonCode, staffId, request).expectStatus().isOk

    val kwConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    kwConfig.verifyAgainst(request)
    verifyAudit(
      kwConfig,
      kwConfig.id,
      RevisionType.ADD,
      setOf(LegacyKeyworkerConfiguration::class.simpleName!!),
      AllocationContext(username = TEST_USERNAME),
    )
  }

  @Test
  fun `keyworker config is updated if it exists`() {
    val prisonCode = "LE2"
    givenPrisonConfig(prisonConfig(prisonCode, true, true))
    val staffId = newId()
    val username = username()
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))

    val request =
      keyworkerConfigRequest(
        status = StaffStatus.UNAVAILABLE_ANNUAL_LEAVE,
        capacity = 100,
        keyworkerStatusBehaviour = KeyworkerStatusBehaviour.KEEP_ALLOCATIONS_NO_AUTO,
        reactivateOn = LocalDate.now().plusDays(7),
      )
    manageKeyworkerConfig(prisonCode, staffId, request, username = username).expectStatus().isOk

    val kwConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    kwConfig.verifyAgainst(request)
    verifyAudit(
      kwConfig,
      kwConfig.id,
      RevisionType.MOD,
      setOf(LegacyKeyworkerConfiguration::class.simpleName!!),
      AllocationContext(username = username),
    )
  }

  @Test
  fun `keyworker config management does not audit no change requests`() {
    val prisonCode = "LE3"
    givenPrisonConfig(prisonConfig(prisonCode, true, true))
    val staffId = newId()
    val username = username()
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId))
    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    val request = keyworkerConfigRequest(status = StaffStatus.ACTIVE, capacity = 6)
    manageKeyworkerConfig(prisonCode, staffId, request, username = username).expectStatus().isOk

    val kwConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    kwConfig.verifyAgainst(request)
    verifyAudit(
      kwConfig,
      kwConfig.id,
      RevisionType.ADD,
      setOf(StaffConfiguration::class.simpleName!!),
      AllocationContext(username = "SYS", activeCaseloadId = null),
    )
    allocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isTrue
      assertThat(it.deallocatedAt).isNull()
      assertThat(it.deallocationReason).isNull()
    }
  }

  @Test
  fun `active allocations are deallocated`() {
    val prisonCode = "LE4"
    givenPrisonConfig(prisonConfig(prisonCode, true, true))
    val staffId = newId()
    givenStaffConfig(staffConfig(StaffStatus.ACTIVE, staffId, capacity = 10))
    val allocations =
      (0..10).map {
        givenAllocation(staffAllocation(personIdentifier(), prisonCode, staffId))
      }

    val request = keyworkerConfigRequest(keyworkerStatusBehaviour = KeyworkerStatusBehaviour.REMOVE_ALLOCATIONS_NO_AUTO)
    manageKeyworkerConfig(prisonCode, staffId, request).expectStatus().isOk

    val kwConfig = requireNotNull(staffConfigRepository.findByStaffId(staffId))
    kwConfig.verifyAgainst(request)

    allocationRepository.findAllById(allocations.map { it.id }).forEach {
      assertThat(it.isActive).isFalse
      assertThat(it.deallocatedAt?.toLocalDate()).isEqualTo(LocalDate.now())
      assertThat(it.deallocationReason?.code).isEqualTo(DeallocationReason.STAFF_STATUS_CHANGE.reasonCode)
    }
  }

  fun keyworkerConfigRequest(
    status: StaffStatus = StaffStatus.ACTIVE,
    capacity: Int = 10,
    keyworkerStatusBehaviour: KeyworkerStatusBehaviour = KeyworkerStatusBehaviour.KEEP_ALLOCATIONS,
    reactivateOn: LocalDate? = null,
  ) = KeyworkerUpdateDto(capacity, status, keyworkerStatusBehaviour, reactivateOn)

  fun manageKeyworkerConfig(
    prisonCode: String,
    staffId: Long,
    request: KeyworkerUpdateDto,
    username: String = TEST_USERNAME,
    role: String? = "ROLE_OMIC_ADMIN",
  ) = webTestClient
    .post()
    .uri(LEGACY_KEYWORKER_CONFIG_URL, staffId, prisonCode)
    .headers(setHeaders(username = username, roles = listOfNotNull(role)))
    .bodyValue(request)
    .exchange()

  companion object {
    const val LEGACY_KEYWORKER_CONFIG_URL = "/key-worker/{staffId}/prison/{prisonId}"
    const val TEST_USERNAME = "T35TUS3R"
  }
}

private fun StaffConfiguration.verifyAgainst(request: KeyworkerUpdateDto) {
  assertThat(status.code).isEqualTo(request.status.name)
  assertThat(capacity).isEqualTo(request.capacity)
  assertThat(allowAutoAllocation).isEqualTo(!request.behaviour.isRemoveFromAutoAllocation)
  assertThat(reactivateOn).isEqualTo(request.activeDate)
}
