package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
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
  private val transactionTemplate: TransactionTemplate,
) {
  fun syncKeyworkerStaffRoles() {
    val originalContext = AllocationContext.get()

    try {
      originalContext.copy(policy = AllocationPolicy.KEY_WORKER).set()
      prisonService.findPolicyEnabledPrisons(AllocationPolicy.KEY_WORKER.name).forEach(::syncKeyworkerStaffRoles)
    } finally {
      originalContext.set()
    }
  }

  fun syncKeyworkerStaffRoles(prisonCode: String) {
    val keyworkers = prisonApi.getKeyworkersForPrison(prisonCode)
    val originalContext = AllocationContext.get()

    try {
      transactionTemplate.execute {
        originalContext.copy(activeCaseloadId = prisonCode, policy = AllocationPolicy.KEY_WORKER).set()
        val activeKeyworkerStaffIds = keyworkers.map { it.staffId }.toSet()
        keyworkers.forEach {
          staffConfigManager.setStaffRole(AllocationPolicy.KEY_WORKER, prisonCode, it.staffId, it.asRequest())
        }
        staffRoleRepository
          .findAllByPrisonCodeAndPolicy(prisonCode, AllocationPolicy.KEY_WORKER.name)
          .filterNot { it.staffId in activeKeyworkerStaffIds }
          .forEach { it.toDate = LocalDate.now() }
      }
    } finally {
      originalContext.set()
    }
  }

  private fun NomisStaffRole.asRequest() = StaffJobClassificationRequest(position, scheduleType, hoursPerWeek, fromDate, toDate)
}
