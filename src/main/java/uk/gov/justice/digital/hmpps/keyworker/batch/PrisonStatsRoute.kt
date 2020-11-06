package uk.gov.justice.digital.hmpps.keyworker.batch

import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerStatsService
import uk.gov.justice.digital.hmpps.keyworker.services.PrisonSupportedService

/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = ["quartz.enabled"])
class PrisonStatsRoute(
        @Autowired private val keyworkerStatsService: KeyworkerStatsService,
        @Autowired private val prisonSupportedService: PrisonSupportedService,
        @Value("\${prisonStats.job.cron}")
        private val cronExpression: String = ""): RouteBuilder() {

    override fun configure() {
        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_PRISON_STATS_URI + cronExpression)
                    .to(DIRECT_PRISON_STATS)
        }
        from(DIRECT_PRISON_STATS)
                .log("Starting: Daily Prison Statistics")
                .bean(prisonSupportedService, "getMigratedPrisons")
                .log("There are \${body.size} migrated prisons")
                .split(body())
                .to(DIRECT_GENERATE_STATS)
                .end()
                .log("Complete: Daily Prison Statistics")
        from(DIRECT_GENERATE_STATS)
                .errorHandler(deadLetterChannel(DIRECT_LOG_ERROR).redeliveryDelay(3000).backOffMultiplier(1.37).maximumRedeliveries(2))
                .log("Gathering stats for \${body.prisonId}")
                .bean(keyworkerStatsService, "generatePrisonStats(\${body.prisonId})")
                .log("Stats completed for \${body.prisonId}")
        from(DIRECT_LOG_ERROR)
                .log(LoggingLevel.ERROR, "Error occurred processing \${body.prisonId}")
                .to("log:stats-error?level=ERROR&showCaughtException=true&showStackTrace=true&showAll=true")
                .bean(keyworkerStatsService, "raiseStatsProcessingError(\${body.prisonId})")
    }

    companion object {
        const val DIRECT_PRISON_STATS = "direct:prisonStats"
        private const val QUARTZ_PRISON_STATS_URI = "quartz2://application/prisonStats?cron="
        private const val DIRECT_GENERATE_STATS = "direct:generate-stats"
        private const val DIRECT_LOG_ERROR = "direct:log-error"
    }
}