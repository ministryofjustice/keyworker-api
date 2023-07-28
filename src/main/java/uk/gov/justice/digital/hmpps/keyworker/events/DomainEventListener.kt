package uk.gov.justice.digital.hmpps.keyworker.events

import com.google.gson.Gson
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class DomainEventListener(
  private val complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor,
  @Qualifier("gson") private val gson: Gson
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("complexityofneed", factory = "hmppsQueueContainerFactoryProxy")
  fun eventListener(requestJson: String) {
    val (message, messageAttributes) = gson.fromJson(requestJson, Message::class.java)
    val eventType = messageAttributes.eventType.Value
    log.info("Processing message of type {}", eventType)

    when (eventType) {
      "complexity-of-need.level.changed" -> complexityOfNeedEventProcessor.onComplexityChange(message)
    }
  }
}
