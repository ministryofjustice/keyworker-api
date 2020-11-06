package uk.gov.justice.digital.hmpps.keyworker.batch

import org.apache.camel.builder.RouteBuilder
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.services.KeyworkerBatchService

/**
 * A Scheduled job that checks for any key workers with reached active dates. KEYWORKER.ACTIVE_DATE
 * and updates their current status to active
 */
@Component
@ConditionalOnProperty(name = ["quartz.enabled"])
class UpdateStatusRoute(
        @Autowired private val service: KeyworkerBatchService,
        @Value("\${updateStatus.job.cron}")
        private val cronExpression: String = "") : RouteBuilder() {

    override fun configure() {
        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_UPDATE_STATUS_URI + cronExpression)
                    .to(DIRECT_UPDATE_STATUS)
        }
        from(DIRECT_UPDATE_STATUS)
                .bean(service, "executeUpdateStatus")
                .log("Keyworkers updated to active status: \${body.size}")
    }

    companion object {
        const val DIRECT_UPDATE_STATUS = "direct:updateStatus"
        private const val QUARTZ_UPDATE_STATUS_URI = "quartz2://application/updateStatus?cron="
    }
}