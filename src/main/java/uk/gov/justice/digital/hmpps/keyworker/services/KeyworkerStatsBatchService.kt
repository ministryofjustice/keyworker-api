package uk.gov.justice.digital.hmpps.keyworker.services

import lombok.extern.slf4j.Slf4j
import org.springframework.retry.RetryCallback
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic
import uk.gov.justice.digital.hmpps.keyworker.services.QueueAdminService.Companion.log

@Service
@Slf4j
class KeyworkerStatsBatchService(
  private val keyworkerStatsService: KeyworkerStatsService,
  private val prisonSupportedService: PrisonSupportedService,
  private val defaultRetryTemplate: RetryTemplate
) {

  fun generatePrisonStats() {
    try {
      val prisonsWithId = prisonSupportedService.migratedPrisons
      log.info("There are %d migrated prisons", prisonsWithId.size)
      for (prison in prisonsWithId.stream()) {
        generatePrisonStatsForPrison(prison.prisonId)
      }
    } catch (e: Exception) {
      log.error("Error occurred reconciling key worker allocations for prisons", e)
    }
  }

  private fun generatePrisonStatsForPrison(prisonId: String) {
    try {
      log.info("Gathering stats for %s", prisonId)
      generatePrisonStatsWithRetry(prisonId)
      log.info("Stats completed for %s", prisonId)
    } catch (e: Exception) {
      log.error("Error occurred processing %s", prisonId)
      keyworkerStatsService.raiseStatsProcessingError(prisonId, e)
    }
  }

  private fun generatePrisonStatsWithRetry(prisonId: String): PrisonKeyWorkerStatistic {
    return defaultRetryTemplate.execute(
      RetryCallback<PrisonKeyWorkerStatistic, RuntimeException> {
        keyworkerStatsService.generatePrisonStats(prisonId)
      }
    )
  }
}
