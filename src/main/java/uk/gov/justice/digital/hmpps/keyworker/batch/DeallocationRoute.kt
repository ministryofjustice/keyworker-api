package uk.gov.justice.digital.hmpps.keyworker.batch

import org.apache.camel.builder.RouteBuilder
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * A Scheduled job that deallocates prisoners that have moved to another prison or have been released.
 */
@Component
@ConditionalOnProperty(name = ["quartz.enabled"])
class DeallocationRoute(
        @Value("\${deallocation.job.cron}")
        private val cronExpression: String
) : RouteBuilder() {

    override fun configure() {
        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_UPDATE_STATUS_URI + cronExpression)
                    .log("*** DEPRECATED: removed de-allocation process")
        }
    }

    companion object {
        private const val QUARTZ_UPDATE_STATUS_URI = "quartz2://application/deallocation?cron="
    }
}