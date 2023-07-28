package uk.gov.justice.digital.hmpps.keyworker.batch

import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.services.NomisBatchService

/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = ["quartz.enabled"])
class EnableNewNomisJob @Autowired constructor(
  private val nomisBatchService: NomisBatchService
) : QuartzJobBean() {

  override fun executeInternal(context: JobExecutionContext) {
    log.info("Starting: Checking for new users and enabling user access to API")
    nomisBatchService.enableNomis()
    log.info("Complete: Checking for new Users and Enabling User access to API")
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    const val ENABLE_NEW_NOMIS = "enableNewNomis"
  }
}
