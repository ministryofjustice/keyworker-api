package uk.gov.justice.digital.hmpps.keyworker.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
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

  companion object {
    const val OFFENDER_NO = "A12345"
    const val OFFENDER_NO_1 = "A12345"
    const val OFFENDER_NO_2 = "A12346"
    const val OFFENDER_NO_3 = "A12347"

    val ENABLED_PRISONS = setOf("MDI")
  }

  @Mock
  lateinit var keyworkerService: KeyworkerService

  @Mock
  lateinit var telemetryClient: TelemetryClient

  @Mock
  lateinit var complexityOfNeedAPI: ComplexityOfNeedAPI

  lateinit var complexityOfNeedService: ComplexityOfNeedService

  @BeforeEach
  fun setUp() {
    complexityOfNeedService =
      ComplexityOfNeedService(keyworkerService, complexityOfNeedAPI, ENABLED_PRISONS, telemetryClient)
  }

  @Test
  fun `should not deallocate offenders that do not have high complexity of needs`() {
    complexityOfNeedService.onComplexityChange(OFFENDER_NO_1, ComplexityOfNeedLevel.LOW)

    verify(keyworkerService, never()).deallocate(OFFENDER_NO_1)
  }

  @Test
  fun `should deallocate offenders that have high complexity of needs`() {
    complexityOfNeedService.onComplexityChange(OFFENDER_NO_1, ComplexityOfNeedLevel.HIGH)

    verify(keyworkerService, times(1)).deallocate(OFFENDER_NO_1)
  }

  @Test
  fun `should not filter out complex offenders for none enabled prisons`() {
    val noneComplexOffenders = complexityOfNeedService.getComplexOffenders("LEI", setOf(OFFENDER_NO_1))

    assertThat(noneComplexOffenders).isEqualTo(setOf(OFFENDER_NO_1))
  }

  @Test
  fun `should make a complexity of need reuqest for enabled prisons`() {
    whenever(complexityOfNeedAPI.getOffendersWithMeasuredComplexityOfNeed(any())).thenReturn(
      listOf(
        ComplexOffender(OFFENDER_NO_1, ComplexityOfNeedLevel.LOW)
      )
    )
    complexityOfNeedService.getComplexOffenders("MDI", setOf(OFFENDER_NO_1))

    verify(complexityOfNeedAPI, times(1)).getOffendersWithMeasuredComplexityOfNeed(setOf(OFFENDER_NO_1))
  }

  @Test
  fun `should remove all offenders that have a high complexity of need`() {
    whenever(complexityOfNeedAPI.getOffendersWithMeasuredComplexityOfNeed(any())).thenReturn(
      listOf(
        ComplexOffender(OFFENDER_NO_1, ComplexityOfNeedLevel.HIGH),
        ComplexOffender(OFFENDER_NO_2, ComplexityOfNeedLevel.LOW)
      )
    )

    val noneHighComplexOffenders =
      complexityOfNeedService.getComplexOffenders("MDI", setOf(OFFENDER_NO_1, OFFENDER_NO_3))

    assertThat(noneHighComplexOffenders).isEqualTo(setOf(OFFENDER_NO_3))
  }

  @Test
  fun `should raise a telemetry event`() {
    complexityOfNeedService.onComplexityChange(OFFENDER_NO, ComplexityOfNeedLevel.LOW)

    Mockito.verify(telemetryClient, Mockito.times(1)).trackEvent(
      "complexity-of-need-change",
      mapOf("offenderNo" to ComplexityOfNeedListenerTest.OFFENDER_NO, "level-changed-to" to "LOW"),
      null
    )
  }
}
