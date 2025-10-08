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
  val relevantMovement: RelevantMovement?,
) {
  val allocations =
    buildList {
      val sorted = history.sortedByDescending { it.assigned }
      add(
        sorted.first().apply {
          relevantMovement?.also {
            deallocatedAt = it.movement?.occurredAt ?: LocalDateTime.now()
            deallocatedBy = SYSTEM_USERNAME
            deallocationReasonCode =
              it.movement?.deallocationReason?.name ?: DeallocationReason.NO_LONGER_IN_PRISON.name
          }
        },
      )
      (1..sorted.lastIndex).forEach { idx ->
        add(
          sorted[idx].apply {
            deallocatedAt = sorted[idx - 1].assigned
            deallocatedBy = sorted[idx - 1].userId
            deallocationReasonCode = DeallocationReason.OVERRIDE.name
          },
        )
      }
    }
}

class RelevantMovement(
  prisonCode: String,
  history: List<DeallocatingMovement>,
) {
  val movement: DeallocatingMovement? =
    history.sortedByDescending { it.occurredAt }.firstOrNull {
      it.deallocationReason != null && it.directionCode == "OUT" && it.fromAgency == prisonCode
    }
}
