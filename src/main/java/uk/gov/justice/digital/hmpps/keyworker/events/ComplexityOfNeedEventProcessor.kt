package uk.gov.justice.digital.hmpps.keyworker.events

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import java.time.LocalDateTime
import java.util.Locale

data class ComplexityOfNeedChange(
  override val apiEndpoint: String,
  override val eventOccurred: LocalDateTime,
  val offenderNo: String,
  val level: String,
  val active: Boolean?,
  // set in code as the event type is actually missing
  override val eventType: String = "complexity-of-need.level.changed",
) : DomainEvent

@Service
class ComplexityOfNeedEventProcessor(
  private val keyworkerService: KeyworkerService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
  @Value("\${complexity_of_need_uri}") private val complexityOfNeedUri: String?,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun onComplexityChange(message: String) {
    if (complexityOfNeedUri.isNullOrEmpty()) {
      log.info("Skipping complexity of need event because it's not enabled")
      return
    }

    val event = objectMapper.readValue<ComplexityOfNeedChange>(message)

    event.active?.let {
      if (!it) {
        log.info("Skipping complexity of need record is not active")
        return
      }
    }

    val complexityLevel = ComplexityOfNeedLevel.valueOf(event.level.uppercase(Locale.getDefault()))

    telemetryClient.trackEvent(
      "complexity-of-need-change",
      mapOf(
        "offenderNo" to event.offenderNo,
        "level-changed-to" to complexityLevel.toString(),
      ),
      null,
    )
    if (complexityLevel != ComplexityOfNeedLevel.HIGH) return

    log.info("Deallocating an offender based on their HIGH complexity of need")
    try {
      keyworkerService.deallocate(event.offenderNo)
    } catch (notFound: EntityNotFoundException) {
      log.warn(notFound.message)
    }
  }
}
