package uk.gov.justice.digital.hmpps.keyworker.events

import com.google.gson.Gson
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService
import java.time.LocalDateTime

@Service
class OffenderEventListener(
  private val reconciliationService: ReconciliationService,
  private val keyworkerService: KeyworkerService,
  @Qualifier("gson") private val gson: Gson,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("offenderevents", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "keyworker-api-offender-event-queue", kind = SpanKind.SERVER)
  fun eventListener(requestJson: String) {
    val (message, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Processing message of type {}", eventType)
    val event = gson.fromJson(message, OffenderEvent::class.java)
    when (eventType) {
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> reconciliationService.checkMovementAndDeallocate(event)
      "BOOKING_NUMBER-CHANGED" -> reconciliationService.checkForMergeAndDeallocate(event.bookingId)
      "DATA_COMPLIANCE_DELETE-OFFENDER" -> keyworkerService.deleteKeyworkersForOffender(event.offenderIdDisplay)
    }
  }

  data class OffenderEvent(
    val bookingId: Long?,
    val movementSeq: Long?,
    val offenderIdDisplay: String?,
    val movementDateTime: LocalDateTime?,
    val movementType: String?,
    val movementReasonCode: String?,
    val directionCode: String?,
    val escortCode: String?,
    val fromAgencyLocationId: String,
    val toAgencyLocationId: String,
  )
}
