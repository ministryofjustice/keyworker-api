package uk.gov.justice.digital.hmpps.keyworker.events

import com.google.gson.Gson
import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.keyworker.config.JsonConfig
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import javax.persistence.EntityNotFoundException

@ExtendWith(MockitoExtension::class)
class ComplexityOfNeedEventProcessorTest {

  @Mock
  lateinit var telemetryClient: TelemetryClient

  @Mock
  lateinit var keyworkerService: KeyworkerService

  lateinit var complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor

  companion object {
    val gson: Gson = JsonConfig().gson()
    const val OFFENDER_NO = "A12345"
  }

  val COMPLEXITY_MESSAGE_HIGH = this::class.java.getResource("complexity-message-high.json").readText()
  val COMPLEXITY_MESSAGE_MEDIUM = this::class.java.getResource("complexity-message-medium.json").readText()
  val COMPLEXITY_MESSAGE_LOW = this::class.java.getResource("complexity-message-low.json").readText()

  @Test
  fun `should not deallocate offenders that do not have high complexity of needs`() {
    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, gson, "http://local")
    complexityOfNeedEventProcessor.onComplexityChange(COMPLEXITY_MESSAGE_LOW)

    verify(keyworkerService, never()).deallocate(OFFENDER_NO)
  }

  @Test
  fun `should deallocate offenders that have high complexity of needs`() {
    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, gson, "http://local")
    complexityOfNeedEventProcessor.onComplexityChange(COMPLEXITY_MESSAGE_HIGH)

    verify(keyworkerService, times(1)).deallocate(OFFENDER_NO)
  }

  @Test
  fun `should raise a telemetry event`() {
    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, gson, "http://local")
    complexityOfNeedEventProcessor.onComplexityChange(COMPLEXITY_MESSAGE_MEDIUM)

    verify(telemetryClient, Mockito.times(1)).trackEvent(
      "complexity-of-need-change",
      mapOf("offenderNo" to OFFENDER_NO, "level-changed-to" to "MEDIUM"),
      null
    )
  }

  @Test
  fun `should do nothing when there is no complexity url`() {
    complexityOfNeedEventProcessor = ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, gson, "")
    complexityOfNeedEventProcessor.onComplexityChange(COMPLEXITY_MESSAGE_HIGH)

    verify(keyworkerService, never()).deallocate(OFFENDER_NO)
    verify(telemetryClient, never()).trackEvent(anyString(), any(), any())
  }

  @Test
  fun `should swallow entity not found exceptions`() {
    whenever(keyworkerService.deallocate(OFFENDER_NO)).thenThrow(EntityNotFoundException::class.java)

    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, gson, "http://local")

    complexityOfNeedEventProcessor.onComplexityChange(COMPLEXITY_MESSAGE_HIGH)
  }
}
