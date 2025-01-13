package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService
import java.time.LocalDateTime

@Service
class OffenderEventListener(
  private val reconciliationService: ReconciliationService,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("offenderevents", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "keyworker-api-offender-event-queue", kind = SpanKind.SERVER)
  fun eventListener(requestJson: String) {
    val (message, messageAttributes) = objectMapper.readValue<Message>(requestJson)
    val eventType = messageAttributes.eventType.value
    log.info("Processing message of type {}", eventType)
    val event = objectMapper.readValue<OffenderEvent>(message)
    when (eventType) {
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> reconciliationService.checkMovementAndDeallocate(event)
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
    val fromAgencyLocationId: String?,
    val toAgencyLocationId: String?,
  )
}
