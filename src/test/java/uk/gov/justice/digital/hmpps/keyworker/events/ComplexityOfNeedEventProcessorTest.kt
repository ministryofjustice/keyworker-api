package uk.gov.justice.digital.hmpps.keyworker.events

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerService
import uk.gov.justice.digital.hmpps.keyworker.utils.JsonHelper.objectMapper

@ExtendWith(MockitoExtension::class)
class ComplexityOfNeedEventProcessorTest {
  @Mock
  lateinit var telemetryClient: TelemetryClient

  @Mock
  lateinit var keyworkerService: KeyworkerService

  lateinit var complexityOfNeedEventProcessor: ComplexityOfNeedEventProcessor

  companion object {
    const val OFFENDER_NO = "A12345"
  }

  private val cOMPLEXITYMESSAGEINACTIVE = this::class.java.getResource("complexity-message-inactive.json").readText()
  private val cOMPLEXITYMESSAGEHIGH = this::class.java.getResource("complexity-message-high.json").readText()
  private val cOMPLEXITYMESSAGEMEDIUM = this::class.java.getResource("complexity-message-medium.json").readText()
  private val cOMPLEXITYMESSAGELOW = this::class.java.getResource("complexity-message-low.json").readText()

  @Test
  fun `should not deallocate offenders that do not have high complexity of needs`() {
    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, objectMapper, "http://local")
    complexityOfNeedEventProcessor.onComplexityChange(cOMPLEXITYMESSAGELOW)

    verify(keyworkerService, never()).deallocate(OFFENDER_NO)
  }

  @Test
  fun `should deallocate offenders that have high complexity of needs`() {
    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, objectMapper, "http://local")
    complexityOfNeedEventProcessor.onComplexityChange(cOMPLEXITYMESSAGEHIGH)

    verify(keyworkerService, times(1)).deallocate(OFFENDER_NO)
  }

  @Test
  fun `should raise a telemetry event`() {
    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, objectMapper, "http://local")
    complexityOfNeedEventProcessor.onComplexityChange(cOMPLEXITYMESSAGEMEDIUM)

    verify(telemetryClient, Mockito.times(1)).trackEvent(
      "complexity-of-need-change",
      mapOf("offenderNo" to OFFENDER_NO, "level-changed-to" to "MEDIUM"),
      null,
    )
  }

  @Test
  fun `should do nothing when there is no complexity url`() {
    complexityOfNeedEventProcessor = ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, objectMapper, "")
    complexityOfNeedEventProcessor.onComplexityChange(cOMPLEXITYMESSAGEHIGH)

    verify(keyworkerService, never()).deallocate(OFFENDER_NO)
    verify(telemetryClient, never()).trackEvent(anyString(), any(), any())
  }

  @Test
  fun `should do nothing when record is not active`() {
    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(
        keyworkerService, telemetryClient, objectMapper, "http://local",
      )
    complexityOfNeedEventProcessor.onComplexityChange(cOMPLEXITYMESSAGEINACTIVE)

    verify(keyworkerService, never()).deallocate(OFFENDER_NO)
    verify(telemetryClient, never()).trackEvent(anyString(), any(), any())
  }

  @Test
  fun `should swallow entity not found exceptions`() {
    whenever(keyworkerService.deallocate(OFFENDER_NO)).thenThrow(EntityNotFoundException::class.java)

    complexityOfNeedEventProcessor =
      ComplexityOfNeedEventProcessor(keyworkerService, telemetryClient, objectMapper, "http://local")

    complexityOfNeedEventProcessor.onComplexityChange(cOMPLEXITYMESSAGEHIGH)
  }
}
