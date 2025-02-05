package uk.gov.justice.digital.hmpps.keyworker.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonSupported
import uk.gov.justice.digital.hmpps.keyworker.repository.PrisonSupportedRepository
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ComplexityOfNeedServiceTest {
  companion object {
    const val OFFENDER_NO_1 = "A12345"
    const val OFFENDER_NO_2 = "A12346"
    const val OFFENDER_NO_3 = "A12347"
  }

  @Mock
  lateinit var prisonSupportedRepository: PrisonSupportedRepository

  @Mock
  lateinit var complexityOfNeedGateway: ComplexityOfNeedGateway

  lateinit var complexityOfNeedService: ComplexityOfNeedService

  @BeforeEach
  fun setUp() {
    complexityOfNeedService = ComplexityOfNeedService(complexityOfNeedGateway, prisonSupportedRepository)
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
        ComplexOffender(OFFENDER_NO_1, ComplexityOfNeedLevel.LOW),
      ),
    )
    whenever(prisonSupportedRepository.findById("MDI"))
      .thenReturn(Optional.of(PrisonSupported("MDI", true, true, LocalDateTime.now(), 6, 9, 1, true)))
    complexityOfNeedService.removeOffendersWithHighComplexityOfNeed("MDI", setOf(OFFENDER_NO_1))

    verify(complexityOfNeedGateway, times(1)).getOffendersWithMeasuredComplexityOfNeed(setOf(OFFENDER_NO_1))
  }

  @Test
  fun `should remove all offenders that have a high complexity of need`() {
    whenever(complexityOfNeedGateway.getOffendersWithMeasuredComplexityOfNeed(any())).thenReturn(
      listOf(
        ComplexOffender(OFFENDER_NO_1, ComplexityOfNeedLevel.HIGH),
        ComplexOffender(OFFENDER_NO_2, ComplexityOfNeedLevel.LOW),
      ),
    )
    whenever(prisonSupportedRepository.findById("MDI"))
      .thenReturn(Optional.of(PrisonSupported("MDI", true, true, LocalDateTime.now(), 6, 9, 1, true)))

    val complexOffenders =
      complexityOfNeedService.removeOffendersWithHighComplexityOfNeed("MDI", setOf(OFFENDER_NO_1, OFFENDER_NO_2, OFFENDER_NO_3))

    assertThat(complexOffenders).isEqualTo(setOf(OFFENDER_NO_3, OFFENDER_NO_2))
  }
}
