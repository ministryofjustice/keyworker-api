package uk.gov.justice.digital.hmpps.keyworker.batch

import org.apache.camel.builder.RouteBuilder
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsBatchService

/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = ["quartz.enabled"])
class PrisonStatsRoute @Autowired constructor(
  private val keyworkerStatsBatchService: KeyworkerStatsBatchService
) : RouteBuilder() {

  @Value("\${prisonStats.job.cron}")
  private val cronExpression: String? = null

  override fun configure() {

    if (StringUtils.isNotBlank(cronExpression)) {
      from(QUARTZ_PRISON_STATS_URI + cronExpression)
        .to(DIRECT_PRISON_STATS)
    }

    from(DIRECT_PRISON_STATS)
      .log("Starting: Daily Prison Statistics")
      .bean(keyworkerStatsBatchService, "generatePrisonStats")
      .log("Complete: Daily Prison Statistics")
  }

  companion object {
    const val DIRECT_PRISON_STATS = "direct:prisonStats"
    private const val QUARTZ_PRISON_STATS_URI = "quartz2://application/prisonStats?cron="
  }
}
