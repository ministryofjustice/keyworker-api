package uk.gov.justice.digital.hmpps.keyworker.integration

import io.jsonwebtoken.security.Jwks.OP.policy
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffConfiguration
import uk.gov.justice.digital.hmpps.keyworker.model.staff.StaffStatus
import java.time.LocalDate

class ReturningFromLeaveIntTest : IntegrationTest() {
  @ParameterizedTest
  @EnumSource(AllocationPolicy::class)
  fun `returning from leave does not update due to return tomorrow`(policy: AllocationPolicy) {
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

  @Test
  fun `Returning from leave updates status to active`() {
    val context = AllocationContext.get().copy(username = SYSTEM_USERNAME, policy = AllocationPolicy.PERSONAL_OFFICER)
    setContext(context)
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

    setContext(context)
    staffConfigRepository.findAllById(configs.map { it.id }).forEach {
      assertThat(it.status.code).isEqualTo(StaffStatus.ACTIVE.name)
      assertThat(it.reactivateOn).isNull()
      verifyAudit(
        it,
        it.id,
        RevisionType.MOD,
        setOf(StaffConfiguration::class.simpleName!!),
        context,
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
