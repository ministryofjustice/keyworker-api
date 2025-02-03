package uk.gov.justice.digital.hmpps.keyworker.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.retry.RetryCallback
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Service

@Service
class ReconciliationBatchService(
  private val reconciliationService: ReconciliationService,
  private val prisonSupportedService: PrisonSupportedService,
  private val defaultRetryTemplate: RetryTemplate,
) {
  val log: Logger = LoggerFactory.getLogger(this.javaClass.name)

  fun reconcileKeyWorkerAllocations() {
    try {
      val prisonsWithId = prisonSupportedService.migratedPrisons
      log.info("There are {} prisons", prisonsWithId.size)
      for (prison in prisonsWithId.stream()) {
        reconcileKeyWorkerAllocationsForPrison(prison.prisonId)
      }
    } catch (e: Exception) {
      log.error("Error occurred reconciling key worker allocations for prisons", e)
    }
  }

  private fun reconcileKeyWorkerAllocationsForPrison(prisonId: String) {
    try {
      log.info("Key Worker Reconciliation for {}", prisonId)
      reconcileKeyWorkersWithRetry(prisonId)
      log.info("Key Worker Reconciliation completed for {}", prisonId)
    } catch (e: Exception) {
      log.error("Error occurred processing {}", prisonId)
      reconciliationService.raiseProcessingError(prisonId, e)
    }
  }

  private fun reconcileKeyWorkersWithRetry(prisonId: String): ReconciliationService.ReconMetrics =
    defaultRetryTemplate.execute(
      RetryCallback<ReconciliationService.ReconMetrics, RuntimeException> {
        reconciliationService.reconcileKeyWorkerAllocations(prisonId)
      },
    )
}
