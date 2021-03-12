package uk.gov.justice.digital.hmpps.keyworker.events

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.ComplexityOfNeed
import java.time.LocalDateTime

@Service
@ConditionalOnExpression("{'aws', 'localstack'}.contains('\${complexity-of-need-sqs.provider}')")
class ComplexityOfNeedListener(
  private val complexityOfNeedService: ComplexityOfNeed,
  @Qualifier("gson") private val gson: Gson
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(
    destination = "\${complexity-of-need-sqs.queue.name}",
    containerFactory = "jmsListenerContainerFactoryForComplexityOfNeed"
  )
  fun eventListener(requestJson: String) {
    val (message, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Processing message of type {}", eventType)
    val event = gson.fromJson(message, ComplexityOfNeedChange::class.java)

    when (eventType) {
      "complexity-of-need.level.changed" -> complexityOfNeedService.onComplexityChange(
        event.offenderNo,
        ComplexityOfNeedLevel.valueOf(
          event.level.toUpperCase()
        )
      )
    }
  }

  data class ComplexityOfNeedChange(
    override val eventType: String,
    override val version: String,
    override val apiEndpoint: String,
    override val eventOccurred: LocalDateTime,
    val offenderNo: String,
    val level: String
  ) : DomainEvent
}
