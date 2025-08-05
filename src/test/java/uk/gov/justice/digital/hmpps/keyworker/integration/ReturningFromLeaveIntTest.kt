package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.model.StaffStatus
import java.time.LocalDate

class ReturningFromLeaveIntTest : IntegrationTest() {
  @ParameterizedTest
  @EnumSource(AllocationPolicy::class)
  fun `returning from leave does update due to return tomorrow`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(username = SYSTEM_USERNAME, policy = policy))
    val config =
      givenStaffConfig(
        staffConfig(StaffStatus.UNAVAILABLE_ANNUAL_LEAVE, reactivateOn = LocalDate.now().plusDays(1)),
      )
    returningFromLeave(LocalDate.now()).expectStatus().isNoContent

    with(requireNotNull(staffConfigRepository.findByIdOrNull(config.id))) {
      assertThat(status.code).isEqualTo(StaffStatus.UNAVAILABLE_ANNUAL_LEAVE.name)
      assertThat(reactivateOn).isEqualTo(LocalDate.now().plusDays(1))
      verifyAudit(
        this,
        id,
        RevisionType.ADD,
        setOf(StaffConfiguration::class.simpleName!!),
        AllocationContext.get().copy(username = SYSTEM_USERNAME, policy = policy),
      )
    }
  }

  @ParameterizedTest
  @EnumSource(AllocationPolicy::class)
  fun `Returning from leave updates status to active`(policy: AllocationPolicy) {
    setContext(AllocationContext.get().copy(username = SYSTEM_USERNAME, policy = policy))
    val configs =
      (0..2).map {
        givenStaffConfig(
          staffConfig(
            StaffStatus.UNAVAILABLE_ANNUAL_LEAVE,
            reactivateOn = LocalDate.now().minusDays(it.toLong()),
          ),
        )
      }

    returningFromLeave(LocalDate.now()).expectStatus().isNoContent

    staffConfigRepository.findAllById(configs.map { it.id }).forEach {
      assertThat(it.status.code).isEqualTo(StaffStatus.ACTIVE.name)
      assertThat(it.reactivateOn).isNull()
      verifyAudit(
        it,
        it.id,
        RevisionType.MOD,
        setOf(StaffConfiguration::class.simpleName!!),
        AllocationContext.get().copy(username = SYSTEM_USERNAME, policy = policy),
      )
    }
  }

  private fun returningFromLeave(date: LocalDate?) =
    webTestClient
      .post()
      .uri { builder ->
        builder.path(RETURN_FROM_LEAVE_URL)
        date?.also { builder.queryParam("date", it) }
        builder.build()
      }.exchange()

  companion object {
    const val RETURN_FROM_LEAVE_URL = "/staff/returning-from-leave"
  }
}
