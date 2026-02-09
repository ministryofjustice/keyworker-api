package uk.gov.justice.digital.hmpps.keyworker.integration.events.offender

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationContext
import uk.gov.justice.digital.hmpps.keyworker.config.AllocationPolicy
import uk.gov.justice.digital.hmpps.keyworker.config.set
import uk.gov.justice.digital.hmpps.keyworker.integration.events.Notification
import uk.gov.justice.digital.hmpps.keyworker.integration.prisonregister.PrisonRegisterClient
import uk.gov.justice.digital.hmpps.keyworker.model.DeallocationReason
import uk.gov.justice.digital.hmpps.keyworker.services.DeallocationService
import java.time.LocalDateTime

@Service
class OffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val deallocationService: DeallocationService,
  private val prisonRegisterApi: PrisonRegisterClient,
) {
  @SqsListener("offenderevents", factory = "hmppsQueueContainerFactoryProxy")
  fun eventListener(requestJson: String) =
    try {
      val notification = objectMapper.readValue<Notification<String>>(requestJson)
      val eventType = notification.eventType
      val event =
        objectMapper
          .readValue<OffenderEvent>(notification.message)
          .takeIf { it.toAgencyLocationId != null && it.offenderIdDisplay != null }
      when (eventType) {
        EXTERNAL_MOVEMENT ->
          event
            ?.deallocationReason()
            ?.also {
              event.movementDateTime?.also { dt -> AllocationContext.get().copy(requestAt = dt, policy = null).set() }
              AllocationPolicy.entries.forEach { policy ->
                AllocationContext.get().copy(policy = policy).set()
                deallocationService.deallocateExpiredAllocations(
                  event.toAgencyLocationId!!,
                  event.offenderIdDisplay!!,
                  it,
                )
              }
            }
        else -> { /*no-op*/ }
      }
    } finally {
      AllocationContext.clear()
    }

  private fun OffenderEvent.deallocationReason(): DeallocationReason? =
    when (directionCode to movementType) {
      "OUT" to "TRN", "IN" to "ADM" -> toAgencyLocationId?.isPrison()?.let { DeallocationReason.TRANSFER }
      "OUT" to "REL" -> DeallocationReason.RELEASED
      else -> null
    }

  private fun String.isPrison(): Boolean = prisonRegisterApi.findPrison(this) != null

  companion object {
    const val EXTERNAL_MOVEMENT = "EXTERNAL_MOVEMENT_RECORD-INSERTED"
  }
}

data class OffenderEvent(
  val bookingId: Long? = null,
  val movementSeq: Long? = null,
  val offenderIdDisplay: String? = null,
  val movementDateTime: LocalDateTime? = null,
  val movementType: String? = null,
  val movementReasonCode: String? = null,
  val directionCode: String? = null,
  val escortCode: String? = null,
  val fromAgencyLocationId: String? = null,
  val toAgencyLocationId: String? = null,
)
