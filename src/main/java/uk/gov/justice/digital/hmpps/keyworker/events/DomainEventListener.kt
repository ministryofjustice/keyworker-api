package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DomainEventListener(
  private val complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor,
  private val objectMapper: ObjectMapper,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("complexityofneed", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "keyworker-api-complexity-event-queue", kind = SpanKind.SERVER)
  fun eventListener(requestJson: String) {
    val (message, messageAttributes) = objectMapper.readValue<Message>(requestJson)
    val eventType = messageAttributes.eventType.value
    log.info("Processing message of type {}", eventType)

    when (eventType) {
      "complexity-of-need.level.changed" -> complexityOfNeedEventProcessor.onComplexityChange(message)
    }
  }
}
