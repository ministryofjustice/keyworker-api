package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import lombok.extern.slf4j.Slf4j
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService

@Service
@ConditionalOnProperty(name = ["sqs.provider"])
@Slf4j
open class EventListener(private val objectMapper: ObjectMapper,
                         private val reconciliationService: ReconciliationService,
                         private val keyworkerService: KeyworkerService) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "\${sqs.queue.name}")
  open fun eventListener(requestJson: String) {
    val (message, messageAttributes) = getMessage(requestJson)
    val eventType = messageAttributes.eventType.value
    log.info("Processing message of type {}", eventType)
    val event = getOffenderEvent(message)
    when (eventType) {
      "EXTERNAL_MOVEMENT_RECORD-INSERTED" -> reconciliationService.checkMovementAndDeallocate(event)
      "BOOKING_NUMBER-CHANGED" -> reconciliationService.checkForMergeAndDeallocate(event)
      "DATA_COMPLIANCE_DELETE-OFFENDER" -> {
        require(!event.offenderIdDisplay.isNullOrBlank()) { "Found blank offender id for $requestJson" }
        keyworkerService.deleteKeyworkersForOffender(event.offenderIdDisplay)
      }
    }
  }

  private fun getMessage(requestJson: String): Message =
      objectMapper.readValue<Message>(requestJson, object : TypeReference<Message>() {})

  private fun getOffenderEvent(requestJson: String): OffenderEvent =
      objectMapper.readValue<OffenderEvent>(requestJson, object : TypeReference<OffenderEvent>() {})

  @JsonIgnoreProperties(ignoreUnknown = true)
  private data class Message @JsonCreator constructor(
      @JsonProperty("Message") val message: String,
      @JsonProperty("MessageAttributes") val messageAttributes: MessageAttributes)

  @JsonIgnoreProperties(ignoreUnknown = true)
  private data class MessageAttributes @JsonCreator constructor(@JsonProperty("eventType") val eventType: Attribute)

  @JsonIgnoreProperties(ignoreUnknown = true)
  private data class Attribute @JsonCreator constructor(@JsonProperty("Value") val value: String)

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class OffenderEvent @JsonCreator constructor(
      @JsonProperty("bookingId") val bookingId: Long?,
      @JsonProperty("movementSeq") val movementSeq: Long?,
      @JsonProperty("offenderIdDisplay") val offenderIdDisplay: String?)
}
