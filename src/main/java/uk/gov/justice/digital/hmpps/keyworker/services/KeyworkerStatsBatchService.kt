package uk.gov.justice.digital.hmpps.keyworker.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.retry.RetryCallback
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.keyworker.model.PrisonKeyWorkerStatistic
import java.time.LocalDate

@Service
class KeyworkerStatsBatchService(
  private val keyworkerStatsService: KeyworkerStatsService,
  private val prisonSupportedService: PrisonSupportedService,
  private val defaultRetryTemplate: RetryTemplate,
) {
  val log: Logger = LoggerFactory.getLogger(this.javaClass.name)

  fun generatePrisonStats(snapshotDate: LocalDate) {
    try {
      val prisonsWithId = prisonSupportedService.migratedPrisons
      log.info("There are {} migrated prisons", prisonsWithId.size)
      prisonsWithId.forEach {
        generatePrisonStatsForPrison(it.prisonId, snapshotDate)
      }
    } catch (e: Exception) {
      log.error("Error occurred reconciling key worker allocations for prisons", e)
    }
  }

  private fun generatePrisonStatsForPrison(
    prisonId: String,
    snapshotDate: LocalDate,
  ) {
    try {
      log.info("Gathering stats for {}", prisonId)
      generatePrisonStatsWithRetry(prisonId, snapshotDate)
      log.info("Stats completed for {}", prisonId)
    } catch (e: Exception) {
      log.error("Error occurred processing {}", prisonId)
      keyworkerStatsService.raiseStatsProcessingError(prisonId, e)
    }
  }

  private fun generatePrisonStatsWithRetry(
    prisonId: String,
    snapshotDate: LocalDate,
  ): PrisonKeyWorkerStatistic =
    defaultRetryTemplate.execute(
      RetryCallback<PrisonKeyWorkerStatistic, RuntimeException> {
        keyworkerStatsService.generatePrisonStats(prisonId, snapshotDate)
      },
    )
}
