package uk.gov.justice.digital.hmpps.keyworker.batch

import com.google.common.collect.ImmutableMap
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.camel.Exchange
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.keyworker.dto.CaseloadUpdate
import uk.gov.justice.digital.hmpps.keyworker.services.NomisService

/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = ["quartz.enabled"])
class EnableNewNomisRoute(
        @Autowired private val nomisService: NomisService,
        @Autowired private val telemetryClient: TelemetryClient,
        @Value("\${enable-new-nomis.job.cron}")
        private val cronExpression: String = ""
) : RouteBuilder() {

    override fun configure() {
        if (StringUtils.isNotBlank(cronExpression)) {
            from(QUARTZ_ENABLE_NEW_NOMIS_URI + cronExpression)
                    .to(ENABLE_NEW_NOMIS)
        }
        from(ENABLE_NEW_NOMIS)
                .log("Starting: Checking for new users and enabling user access to API")
                .bean(nomisService, "getAllPrisons")
                .log("There are \${body.size} prisons")
                .split(body())
                .to(ADD_NWEB_CASELOAD)
                .end()
                .log("Complete: Checking for new Users and Enabling User access to API")
        from(ADD_NWEB_CASELOAD)
                .errorHandler(deadLetterChannel(DLQ).logRetryAttempted(true).redeliveryDelay(5000).backOffMultiplier(0.29).maximumRedeliveries(2))
                .setProperty("prisonId", simple("\${body.prisonId}"))
                .log("Starting: Enabling API access for all active users in prison \${property.prisonId}")
                .bean(nomisService, "enableNewNomisForCaseload(\${property.prisonId})")
                .choice().`when`(simple("\${body.numUsersEnabled} > 0"))
                .log("Enabled \${body.numUsersEnabled} new users for API access")
                .process { p: Exchange ->
                    val caseloadRes = p.getIn().getBody(CaseloadUpdate::class.java)
                    val infoMap = ImmutableMap.of(
                            "prisonId", caseloadRes.caseload,
                            "numUsersEnabled", caseloadRes.numUsersEnabled.toString())
                    telemetryClient.trackEvent("ApiUsersEnabled", infoMap, null) //-
                }
                .end()
                .log("Complete: Enabling API access for all active users in prison \${property.prisonId}")
        from(DLQ)
                .log(LoggingLevel.ERROR, "Error occurred processing \${body.prisonId}")
                .to("log:stats-error?level=ERROR&showCaughtException=true&showStackTrace=true&showAll=true")
    }

    companion object {
        const val ENABLE_NEW_NOMIS = "direct:enableNewNomis"
        private const val QUARTZ_ENABLE_NEW_NOMIS_URI = "quartz2://application/enableNewNomis?cron="
        private const val ADD_NWEB_CASELOAD = "direct:add-nweb-caseload"
        private const val DLQ = "direct:dlq"
    }
}