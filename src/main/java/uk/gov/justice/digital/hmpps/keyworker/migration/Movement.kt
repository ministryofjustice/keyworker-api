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
  val fromAgency: String?,
  val toAgency: String?,
  val movementType: String?,
  val movementReasonCode: String?,
  val directionCode: String?,
  @field:JsonAlias("createDateTime")
  val createdAt: LocalDateTime,
) {
  val occurredAt: LocalDateTime = lazy { LocalDateTime.of(movementDate, movementTime) }.value
  val deallocationReason: DeallocationReason? =
    lazy {
      when (movementType) {
        "TRN" if (directionCode == "OUT") -> DeallocationReason.TRANSFER
        "REL" if (directionCode == "OUT") -> DeallocationReason.RELEASED
        "ADM" if (directionCode == "IN") -> DeallocationReason.TRANSFER
        else -> null
      }
    }.value
}
