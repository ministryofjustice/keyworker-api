package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.MergeInformation
import uk.gov.justice.digital.hmpps.keyworker.services.MergePrisonNumbers
import java.time.ZonedDateTime

@Service
class DomainEventListener(
  private val complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor,
  private val mergePrisonNumbers: MergePrisonNumbers,
  private val objectMapper: ObjectMapper,
) {
  @SqsListener("domaineventsqueue", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "keyworker-api-complexity-event-queue", kind = SpanKind.SERVER)
  fun eventListener(requestJson: String) {
    val (message, messageAttributes) = objectMapper.readValue<Message>(requestJson)
    val eventType = messageAttributes.eventType.value

    when (eventType) {
      COMPLEXITY_OF_NEED_CHANGED -> complexityOfNeedEventProcessor.onComplexityChange(message)
      PRISONER_MERGED -> {
        val domainEvent = objectMapper.readValue<DomainEvent<MergeInformation>>(message)
        mergePrisonNumbers.merge(domainEvent.additionalInformation)
      }
    }
  }

  companion object {
    const val PRISONER_MERGED = "prison-offender-events.prisoner.merged"
    const val COMPLEXITY_OF_NEED_CHANGED = "complexity-of-need.level.changed"
  }
}

data class DomainEvent<T : AdditionalInformation>(
  val occurredAt: ZonedDateTime,
  val eventType: String,
  val detailUrl: String?,
  val description: String,
  val additionalInformation: T,
  val personReference: PersonReference,
  val version: Int = 1,
)

interface AdditionalInformation

data class PersonReference(val identifiers: Set<Identifier> = setOf()) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value

  fun findNomsNumber() = get(NOMS_NUMBER_TYPE)

  companion object {
    private const val NOMS_NUMBER_TYPE = "NOMS"

    fun withIdentifier(prisonNumber: String) = PersonReference(setOf(Identifier(NOMS_NUMBER_TYPE, prisonNumber)))
  }

  data class Identifier(val type: String, val value: String)
}
