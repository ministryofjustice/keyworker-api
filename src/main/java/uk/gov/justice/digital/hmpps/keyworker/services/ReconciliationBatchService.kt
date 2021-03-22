package uk.gov.justice.digital.hmpps.keyworker.services

import lombok.extern.slf4j.Slf4j
import org.springframework.retry.RetryCallback
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.services.QueueAdminService.Companion.log

@Service
@Slf4j
class ReconciliationBatchService(private val reconciliationService: ReconciliationService,
                                 private val prisonSupportedService: PrisonSupportedService,
                                 private val defaultRetryTemplate: RetryTemplate) {

  fun reconcileKeyWorkerAllocations() {
    try {
      val prisonsWithId  = prisonSupportedService.migratedPrisons
      log.info("There are %d prisons", prisonsWithId.size)
      for (prison in prisonsWithId.stream()) {
        reconcileKeyWorkerAllocationsForPrison(prison.prisonId)
      }
    } catch (e: Exception ) {
      log.error("Error occurred reconciling key worker allocations for prisons", e)
    }
  }

  private fun reconcileKeyWorkerAllocationsForPrison(prisonId: String) {
    try {
      log.info("Key Worker Reconciliation for %s", prisonId)
      reconcileKeyWorkersWithRetry(prisonId)
      log.info("Key Worker Reconciliation completed for %s", prisonId)
    } catch (e: Exception ) {
      log.error("Error occurred processing %s", prisonId)
      reconciliationService.raiseProcessingError(prisonId, e)
    }
  }

  private fun reconcileKeyWorkersWithRetry(prisonId: String): ReconciliationService.ReconMetrics {
    return defaultRetryTemplate.execute(RetryCallback<ReconciliationService.ReconMetrics, RuntimeException> {
      reconciliationService.reconcileKeyWorkerAllocations(prisonId)
    })
  }
}