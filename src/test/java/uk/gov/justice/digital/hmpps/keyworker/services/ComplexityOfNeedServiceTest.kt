package uk.gov.justice.digital.hmpps.keyworker.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedListenerTest

@ExtendWith(MockitoExtension::class)
class ComplexityOfNeedServiceTest {

  @Mock
  lateinit var keyworkerService: KeyworkerService

  @Mock
  lateinit var telemetryClient: TelemetryClient

  lateinit var complexityOfNeedService: ComplexityOfNeedService

  companion object {
    const val OFFENDER_NO = "A12345"
  }

  @BeforeEach
  fun setUp() {
    complexityOfNeedService = ComplexityOfNeedService(keyworkerService, telemetryClient)
  }

  @Test
  fun `should not deallocate offenders that do not have high complexity of needs`() {
    complexityOfNeedService.onComplexityChange(OFFENDER_NO, ComplexityOfNeedLevel.LOW)

    verify(keyworkerService, never()).deallocate(OFFENDER_NO)
  }

  @Test
  fun `should deallocate offenders that have high complexity of needs`() {
    complexityOfNeedService.onComplexityChange(OFFENDER_NO, ComplexityOfNeedLevel.HIGH)

    verify(keyworkerService, times(1)).deallocate(OFFENDER_NO)
  }

  @Test
  fun `should raise a telemetry event`() {
    complexityOfNeedService.onComplexityChange(OFFENDER_NO, ComplexityOfNeedLevel.LOW)

    Mockito.verify(telemetryClient, Mockito.times(1)).trackEvent(
      "Complexity-of-need-change",
      mapOf("offenderNo" to ComplexityOfNeedListenerTest.OFFENDER_NO, "level-changed-to" to "LOW"),
      null
    )
  }
}
