package uk.gov.justice.digital.hmpps.keyworker.services

import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
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
  }

  @Mock
  lateinit var keyworkerService: KeyworkerService

  lateinit var complexityOfNeedService: ComplexityOfNeedService

  @BeforeEach
  fun setUp() {
    complexityOfNeedService = ComplexityOfNeedService(keyworkerService)
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
}
