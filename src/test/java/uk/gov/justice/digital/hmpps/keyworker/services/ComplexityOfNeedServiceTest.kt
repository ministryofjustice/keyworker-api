package uk.gov.justice.digital.hmpps.keyworker.services

import com.microsoft.applicationinsights.TelemetryClient
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel

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
  lateinit var complexityOfNeedGateway: ComplexityOfNeedGateway

  lateinit var complexityOfNeedService: ComplexityOfNeedService

  @BeforeEach
  fun setUp() {
    complexityOfNeedService =
      ComplexityOfNeedService(complexityOfNeedGateway, ENABLED_PRISONS, telemetryClient)
  }

  @Test
  fun `should report when a prison is high complexity`() {
    val highComplexity = complexityOfNeedService.isComplexPrison("MDI")

    assertThat(highComplexity).isTrue()
  }

  @Test
  fun `should report when a prison is not high complexity`() {
    val notHighComplexity = complexityOfNeedService.isComplexPrison("LEI")

    assertThat(notHighComplexity).isFalse()
  }

  @Test
  fun `should not filter out complex offenders for none enabled prisons`() {
    val complexOffenders = complexityOfNeedService.removeOffendersWithHighComplexityOfNeed("LEI", setOf(OFFENDER_NO_1))

    assertThat(complexOffenders).isEqualTo(setOf(OFFENDER_NO_1))
  }

  @Test
  fun `should make a complexity of need reuqest for enabled prisons`() {
    whenever(complexityOfNeedGateway.getOffendersWithMeasuredComplexityOfNeed(any())).thenReturn(
      listOf(
        ComplexOffender(OFFENDER_NO_1, ComplexityOfNeedLevel.LOW)
      )
    )
    complexityOfNeedService.removeOffendersWithHighComplexityOfNeed("MDI", setOf(OFFENDER_NO_1))

    verify(complexityOfNeedGateway, times(1)).getOffendersWithMeasuredComplexityOfNeed(setOf(OFFENDER_NO_1))
  }

  @Test
  fun `should remove all offenders that have a high complexity of need`() {
    whenever(complexityOfNeedGateway.getOffendersWithMeasuredComplexityOfNeed(any())).thenReturn(
      listOf(
        ComplexOffender(OFFENDER_NO_1, ComplexityOfNeedLevel.HIGH),
        ComplexOffender(OFFENDER_NO_2, ComplexityOfNeedLevel.LOW)
      )
    )

    val complexOffenders =
      complexityOfNeedService.removeOffendersWithHighComplexityOfNeed("MDI", setOf(OFFENDER_NO_1, OFFENDER_NO_2, OFFENDER_NO_3))

    assertThat(complexOffenders).isEqualTo(setOf(OFFENDER_NO_3, OFFENDER_NO_2))
  }
}
