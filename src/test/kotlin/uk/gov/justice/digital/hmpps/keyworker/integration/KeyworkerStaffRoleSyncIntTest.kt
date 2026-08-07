package uk.gov.justice.digital.hmpps.keyworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.PolicyHeader
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.EventType
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.KeyworkerPrisonInformation
import uk.gov.justice.digital.hmpps.keyworker.services.staff.KeyworkerStaffRoleSync
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.newId
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisIdGenerator.prisonCode
import uk.gov.justice.digital.hmpps.keyworker.utils.NomisStaffGenerator.nomisStaffRole
import java.math.BigDecimal
import java.time.LocalDate

class KeyworkerStaffRoleSyncIntTest(
  @Autowired private val ksrSync: KeyworkerStaffRoleSync
) : IntegrationTest() {
  @Test
  fun `syncs keyworker staff roles from NOMIS to local staff roles`() {
    val prisonCode = prisonCode()
    val retainedStaffId = newId()
    val removedStaffId = newId()
    val addedStaffId = newId()
    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    givenPrisonConfig(prisonConfig(prisonCode, enabled = true, policy = AllocationPolicy.KEY_WORKER))
    givenStaffRole(
      staffRole(
        prisonCode,
        retainedStaffId,
        scheduleType = "PT",
        hoursPerWeek = BigDecimal(20),
        fromDate = LocalDate.now().minusMonths(1),
      ),
    )
    givenStaffRole(staffRole(prisonCode, removedStaffId))
    prisonMockServer.stubKeyworkerSearch(
      prisonCode,
      listOf(
        nomisStaffRole(
          retainedStaffId,
          position = "PRO",
          scheduleType = "FT",
          hoursPerWeek = BigDecimal(37.5),
          fromDate = LocalDate.now().minusDays(10),
        ),
        nomisStaffRole(
          addedStaffId,
          position = "AO",
          scheduleType = "SESS",
          hoursPerWeek = BigDecimal(15),
          fromDate = LocalDate.now().minusDays(5),
        ),
      ),
    )

    ksrSync.syncKeyworkerStaffRoles(HmppsDomainEvent(EventType.SyncKeyworkers.name, KeyworkerPrisonInformation(prisonCode)))

    setContext(AllocationContext.get().copy(policy = AllocationPolicy.KEY_WORKER))
    val retained = requireNotNull(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, retainedStaffId))
    assertThat(retained.scheduleType.code).isEqualTo("FT")
    assertThat(retained.hoursPerWeek).isEqualTo(BigDecimal(37.5))
    assertThat(retained.toDate).isNull()

    val added = requireNotNull(staffRoleRepository.findByPrisonCodeAndStaffId(prisonCode, addedStaffId))
    assertThat(added.position.code).isEqualTo("AO")
    assertThat(added.scheduleType.code).isEqualTo("SESS")
    assertThat(added.hoursPerWeek).isEqualTo(BigDecimal(15))
    assertThat(added.toDate).isNull()

    val removed =
      requireNotNull(
        staffRoleRepository.findRoleIncludingInactiveForPolicy(prisonCode, removedStaffId, AllocationPolicy.KEY_WORKER.name),
      )
    assertThat(removed.toDate).isEqualTo(LocalDate.now())
    verifyAudit(
      removed,
      removed.id,
      RevisionType.MOD,
      setOf(StaffRole::class.simpleName!!),
      AllocationContext(username = SYSTEM_USERNAME, policy = AllocationPolicy.KEY_WORKER),
    )
  }
}
