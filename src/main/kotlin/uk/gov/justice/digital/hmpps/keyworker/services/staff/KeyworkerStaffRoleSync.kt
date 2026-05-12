package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonService
import java.time.LocalDate

@Service
class KeyworkerStaffRoleSync(
  private val prisonService: PrisonService,
  private val prisonApi: PrisonApiClient,
  private val staffConfigManager: StaffConfigManager,
  private val staffRoleRepository: StaffRoleRepository,
) {
  @Transactional
  fun syncKeyworkerStaffRoles() {
    prisonService.findPolicyEnabledPrisons(AllocationPolicy.KEY_WORKER.name).forEach(::syncKeyworkerStaffRoles)
  }

  fun syncKeyworkerStaffRoles(prisonCode: String) {
    AllocationContext.get().copy(username = SYSTEM_USERNAME, policy = AllocationPolicy.KEY_WORKER).set()
    val keyworkers = prisonApi.getKeyworkersForPrison(prisonCode)
    val activeKeyworkerStaffIds = keyworkers.map { it.staffId }.toSet()
    keyworkers.forEach {
      staffConfigManager.setStaffRole(prisonCode, it.staffId, it.asRequest())
    }
    staffRoleRepository
      .findAllByPrisonCode(prisonCode)
      .filterNot { it.staffId in activeKeyworkerStaffIds }
      .forEach { it.toDate = LocalDate.now() }
  }

  private fun NomisStaffRole.asRequest() = StaffJobClassificationRequest(position, scheduleType, hoursPerWeek, fromDate, toDate)
}
