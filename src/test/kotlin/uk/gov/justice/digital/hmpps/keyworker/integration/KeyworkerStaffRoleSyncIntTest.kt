package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.nomisStaffRole
import java.math.BigDecimal
import java.time.LocalDate

class KeyworkerStaffRoleSyncIntTest : IntegrationTest() {
  @Test
  fun `syncs keyworker staff roles from NOMIS to local staff roles`() {
    val prisonCode = "KWS"
    val retainedStaffId = newStaffId()
    val removedStaffId = newStaffId()
    val addedStaffId = newStaffId()
    val fromDate = LocalDate.now().minusDays(7)

    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true, policy = AllocationPolicy.KEY_WORKER))
    givenStaffRole(staffRole(prisonCode, retainedStaffId, position = "PRO", scheduleType = "FT", hoursPerWeek = BigDecimal(37.5)))
    givenStaffRole(staffRole(prisonCode, removedStaffId, position = "PRO", scheduleType = "FT", hoursPerWeek = BigDecimal(37.5)))

    prisonMockServer.stubKeyworkerSearch(
      prisonCode,
      listOf(
        nomisStaffRole(
          retainedStaffId,
          position = "PPO",
          scheduleType = "PT",
          hoursPerWeek = BigDecimal(20),
          fromDate = fromDate,
        ),
        nomisStaffRole(
          addedStaffId,
          position = "PRO",
          scheduleType = "FT",
          hoursPerWeek = BigDecimal(40),
          fromDate = fromDate,
        ),
      ),
    )

    webTestClient
      .post()
      .uri(SYNC_URL)
      .exchange()
      .expectStatus()
      .isNoContent

    val retained =
      requireNotNull(
        staffRoleRepository.findRoleIncludingInactiveForPolicy(
          prisonCode,
          retainedStaffId,
          AllocationPolicy.KEY_WORKER.name,
        ),
      )
    assertThat(retained.position.code).isEqualTo("PPO")
    assertThat(retained.scheduleType.code).isEqualTo("PT")
    assertThat(retained.hoursPerWeek).isEqualTo(BigDecimal(20))
    assertThat(retained.fromDate).isEqualTo(fromDate)
    assertThat(retained.toDate).isNull()

    val added =
      requireNotNull(
        staffRoleRepository.findRoleIncludingInactiveForPolicy(
          prisonCode,
          addedStaffId,
          AllocationPolicy.KEY_WORKER.name,
        ),
      )
    assertThat(added.position.code).isEqualTo("PRO")
    assertThat(added.scheduleType.code).isEqualTo("FT")
    assertThat(added.hoursPerWeek).isEqualTo(BigDecimal(40))
    assertThat(added.fromDate).isEqualTo(fromDate)
    assertThat(added.toDate).isNull()

    val removed =
      requireNotNull(
        staffRoleRepository.findRoleIncludingInactiveForPolicy(
          prisonCode,
          removedStaffId,
          AllocationPolicy.KEY_WORKER.name,
        ),
      )
    assertThat(removed.toDate).isEqualTo(LocalDate.now())
  }

  companion object {
    private const val SYNC_URL = "/staff/keyworker-roles/sync"
    private val STAFF_ID_OFFSET = (System.currentTimeMillis() % 100_000L) * 1_000L

    private fun newStaffId() = STAFF_ID_OFFSET + newId()
  }
}
