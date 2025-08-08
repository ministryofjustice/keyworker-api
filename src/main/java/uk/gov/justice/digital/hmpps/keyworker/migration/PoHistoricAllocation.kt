package uk.gov.justice.digital.hmpps.keyworker.migration

import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import java.time.LocalDateTime

data class PoHistoricAllocation(
  val agencyId: String,
  val offenderNo: String,
  val staffId: Long,
  val userId: String,
  val assigned: LocalDateTime,
  val created: LocalDateTime,
  val createdBy: String,
) {
  var deallocatedAt: LocalDateTime? = null
  var deallocatedBy: String? = null
  var deallocationReasonCode: String? = null
}

class PoAllocationHistory(
  history: List<PoHistoricAllocation>,
  val movementHistory: MovementHistory?,
) {
  val allocations =
    buildList {
      val sorted = history.sortedByDescending { it.assigned }
      add(
        sorted.first().apply {
          movementHistory?.also {
            val lastMovement = it.latestFromPrison() ?: it.earliestTransfer()
            deallocatedAt = lastMovement?.occurredAt ?: LocalDateTime.now()
            deallocatedBy = SYSTEM_USERNAME
            deallocationReasonCode = lastMovement?.deallocationReason?.reasonCode ?: DeallocationReason.MISSING.reasonCode
          }
        },
      )
      (1..sorted.lastIndex).forEach { idx ->
        add(
          sorted[idx].apply {
            deallocatedAt = sorted[idx - 1].assigned
            deallocatedBy = sorted[idx - 1].userId
            deallocationReasonCode = DeallocationReason.OVERRIDE.reasonCode
          },
        )
      }
    }
}

class MovementHistory(
  val prisonCode: String,
  history: List<Movement>,
) {
  val movements = history.sortedByDescending { it.occurredAt }

  fun latestFromPrison() = movements.firstOrNull { it.directionCode == "OUT" && it.fromAgency == prisonCode }

  fun earliestTransfer() = movements.lastOrNull { it.movementType == "ADM" && it.toAgency != prisonCode }
}
