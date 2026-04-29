package uk.gov.justice.digital.hmpps.keyworker.services.staff

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT
import org.springframework.transaction.event.TransactionalEventListener
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.NomisUserRolesApiClient
import uk.gov.justice.digital.hmpps.keyworker.integration.nomisuserroles.StaffJobClassificationRequest

data class NomisStaffRoleUpdateEvent(
  val prisonCode: String,
  val staffId: Long,
  val role: String,
  val request: StaffJobClassificationRequest,
)

@Component
class NomisStaffRoleUpdateListener(
  private val nomisUserRolesApiClient: NomisUserRolesApiClient,
) {
  @Async
  @TransactionalEventListener(phase = AFTER_COMMIT)
  fun updateNomisStaffRole(event: NomisStaffRoleUpdateEvent) {
    runCatching {
      nomisUserRolesApiClient.setStaffRole(event.prisonCode, event.staffId, event.role, event.request)
    }.onFailure {
      log.warn("Failed to update NOMIS staff role {} for staff {} at {}", event.role, event.staffId, event.prisonCode, it)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
