package uk.gov.justice.digital.hmpps.keyworker.batch

import org.apache.camel.builder.RouteBuilder
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.services.ReconciliationBatchService

/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = ["quartz.enabled"])
class KeyworkerReconRoute @Autowired constructor(
  private val reconciliationBatchService: ReconciliationBatchService
) : RouteBuilder() {

  @Value("\${key.worker.recon.job.cron}")
  private val cronExpression: String? = null

  override fun configure() {
    context.isStreamCaching = true
    if (StringUtils.isNotBlank(cronExpression)) {
      from(QUARTZ_KEY_WORKER_RECON_URI + cronExpression)
        .to(DIRECT_KEY_WORKER_RECON)
    }
    from(DIRECT_KEY_WORKER_RECON)
      .log("Starting: Key Worker Reconciliation")
      .bean(reconciliationBatchService, "reconcileKeyWorkerAllocations")
      .log("Complete: Key Worker Reconciliation")
  }

  companion object {
    const val DIRECT_KEY_WORKER_RECON = "direct:keyWorkerRecon"
    private const val QUARTZ_KEY_WORKER_RECON_URI = "quartz://application/keyWorkerReconJob?cron="
  }
}
