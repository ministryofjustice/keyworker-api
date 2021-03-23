package uk.gov.justice.digital.hmpps.keyworker.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.keyworker.config.RetryConfiguration
import uk.gov.justice.digital.hmpps.keyworker.dto.Prison
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationService.ReconMetrics

@ExtendWith(SpringExtension::class)
@ContextConfiguration(classes = [ReconciliationBatchService::class, RetryConfiguration::class])
class ReconciliationBatchServiceTest {
  @Autowired
  private lateinit var batchService: ReconciliationBatchService

  @MockBean
  private lateinit var reconciliationService: ReconciliationService

  @MockBean
  private lateinit var prisonSupportedService: PrisonSupportedService

  @Test
  fun testReconcileKeyWorkerAllocations_callsServices() {
    val prisons = listOf(
      MDI,
      LEI,
      LPI
    )
    `when`(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    `when`(reconciliationService.reconcileKeyWorkerAllocations(MDI.prisonId))
      .thenReturn(ReconMetrics(MDI.prisonId, 10, 0))
    `when`(reconciliationService.reconcileKeyWorkerAllocations(LEI.prisonId))
      .thenReturn(ReconMetrics(LEI.prisonId, 5, 1))
    `when`(reconciliationService.reconcileKeyWorkerAllocations(LPI.prisonId))
      .thenReturn(ReconMetrics(LPI.prisonId, 3, 2))

    batchService.reconcileKeyWorkerAllocations()

    verify(prisonSupportedService).migratedPrisons
    verify(reconciliationService).reconcileKeyWorkerAllocations(eq(MDI.prisonId))
    verify(reconciliationService).reconcileKeyWorkerAllocations(eq(LEI.prisonId))
    verify(reconciliationService).reconcileKeyWorkerAllocations(eq(LPI.prisonId))
    verify(reconciliationService, never())
      .raiseProcessingError(anyString(), any())
  }

  @Test
  fun testReconcileKeyWorkerAllocations_npOpOnMigratedPrisonsError() {
    `when`(prisonSupportedService.migratedPrisons).thenThrow(RuntimeException("Error"))

    batchService.reconcileKeyWorkerAllocations()

    verify(prisonSupportedService).migratedPrisons
    verify(reconciliationService, never()).reconcileKeyWorkerAllocations(anyString())
    verify(reconciliationService, never())
      .raiseProcessingError(anyString(), any())
  }

  @Test
  fun testReconcileKeyWorkerAllocations_retriesOnReconcileAllocationsError() {
    val prisons = listOf(
      MDI
    )
    `when`(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    `when`(reconciliationService.reconcileKeyWorkerAllocations(MDI.prisonId))
      .thenThrow(RuntimeException("Error"))
      .thenReturn(ReconMetrics(MDI.prisonId, 10, 0))

    batchService.reconcileKeyWorkerAllocations()

    verify(prisonSupportedService).migratedPrisons
    verify(reconciliationService, times(2))
      .reconcileKeyWorkerAllocations(eq(MDI.prisonId))
    verify(reconciliationService, never())
      .raiseProcessingError(anyString(), any())
  }

  @Test
  fun testReconcileKeyWorkerAllocations_raisesProcessingErrorOnReconcileAllocationsError() {
    val prisons = listOf(
      MDI
    )
    val testException = RuntimeException("Error")
    `when`(prisonSupportedService.migratedPrisons).thenReturn(prisons)
    `when`(reconciliationService.reconcileKeyWorkerAllocations(MDI.prisonId))
      .thenThrow(testException)

    batchService.reconcileKeyWorkerAllocations()

    verify(prisonSupportedService).migratedPrisons
    verify(reconciliationService, times(3))
      .reconcileKeyWorkerAllocations(eq(MDI.prisonId))
    verify(reconciliationService)
      .raiseProcessingError(eq(MDI.prisonId), eq(testException))
  }

  companion object {
    private val MDI = Prison.builder().prisonId("MDI").build()
    private val LEI = Prison.builder().prisonId("LEI").build()
    private val LPI = Prison.builder().prisonId("LPI").build()
  }
}
