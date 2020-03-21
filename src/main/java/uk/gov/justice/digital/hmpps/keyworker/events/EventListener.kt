package uk.gov.justice.digital.hmpps.keyworker.events

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService
import java.time.LocalDateTime

@Service
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${sqs.provider}')")
open class EventListener(private val reconciliationService: ReconciliationService,
                         private val keyworkerService: KeyworkerService,
                         @Qualifier("gson") private val gson: Gson) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "\${sqs.queue.name}")
  open fun eventListener(requestJson: String) {
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

  private data class Message(val Message: String, val MessageAttributes: MessageAttributes)
  private data class MessageAttributes(val eventType: Attribute)
  private data class Attribute(val Value: String)
  data class OffenderEvent(val bookingId: Long?,
                           val movementSeq: Long?,
                           val offenderIdDisplay: String?,
                           val movementDateTime: LocalDateTime?,
                           val movementType: String?,
                           val movementReasonCode: String?,
                           val directionCode: String?,
                           val escortCode: String?,
                           val fromAgencyLocationId: String,
                           val toAgencyLocationId: String
  )
}
