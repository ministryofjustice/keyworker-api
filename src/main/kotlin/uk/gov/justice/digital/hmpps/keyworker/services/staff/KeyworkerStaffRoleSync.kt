package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.domain.StaffRoleRepository
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.keyworker.integration.events.domain.KeyworkerPrisonInformation
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.NomisStaffRole
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonapi.PrisonApiClient
import java.time.LocalDate

@Transactional
@Service
class KeyworkerStaffRoleSync(
  private val prisonApi: PrisonApiClient,
  private val staffConfigManager: StaffConfigManager,
  private val staffRoleRepository: StaffRoleRepository,
) {
  fun syncKeyworkerStaffRoles(de: HmppsDomainEvent<KeyworkerPrisonInformation>) =
    with(de.additionalInformation) {
      AllocationContext.get().copy(username = SYSTEM_USERNAME, policy = AllocationPolicy.KEY_WORKER).set()
      val keyworkers = prisonApi.getKeyworkersForPrison(prisonCode)
      val activeKeyworkerStaffIds = keyworkers.map { it.staffId }.toSet()
      keyworkers.forEach { staffConfigManager.setStaffRole(prisonCode, it.staffId, it.asRequest()) }
      staffRoleRepository
        .findAllByPrisonCode(prisonCode)
        .filterNot { it.staffId in activeKeyworkerStaffIds }
        .forEach { it.toDate = LocalDate.now() }
    }

  private fun NomisStaffRole.asRequest() = StaffJobClassificationRequest(position, scheduleType, hoursPerWeek, fromDate, toDate)
}
