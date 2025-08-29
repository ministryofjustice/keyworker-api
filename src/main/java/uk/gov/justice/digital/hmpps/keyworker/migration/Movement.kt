package uk.gov.justice.digital.hmpps.keyworker.migration

import com.fasterxml.jackson.annotation.JsonAlias
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Movement(
  val offenderNo: String,
  val movementDate: LocalDate,
  val movementTime: LocalTime,
  override val fromAgency: String?,
  val toAgency: String?,
  val movementType: String?,
  val movementReasonCode: String?,
  override val directionCode: String?,
  @field:JsonAlias("createDateTime")
  val createdAt: LocalDateTime,
) : DeallocatingMovement {
  override val occurredAt: LocalDateTime = lazy { LocalDateTime.of(movementDate, movementTime) }.value
  override val deallocationReason: DeallocationReason? =
    lazy {
      when (movementType) {
        "TRN" if (directionCode == "OUT") -> DeallocationReason.TRANSFER
        "REL" if (directionCode == "OUT") -> DeallocationReason.RELEASED
        "ADM" if (directionCode == "IN") -> DeallocationReason.TRANSFER
        else -> null
      }
    }.value
}

interface DeallocatingMovement {
  val directionCode: String?
  val fromAgency: String?
  val occurredAt: LocalDateTime?
  val deallocationReason: DeallocationReason?
}

data class DeallocateAll(
  override val fromAgency: String?,
  override val deallocationReason: DeallocationReason = DeallocationReason.PRISON_USES_KEY_WORK,
  override val occurredAt: LocalDateTime = LocalDateTime.now(),
  override val directionCode: String = "OUT",
) : DeallocatingMovement
