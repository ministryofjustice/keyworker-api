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
import uk.gov.justice.digital.hmpps.keyworker.services.NomisBatchService
import uk.gov.justice.digital.hmpps.keyworker.services.NomisService

/**
 * A Scheduled job that checks builds stats for each prison for the previous day
 */
@Component
@ConditionalOnProperty(name = ["quartz.enabled"])
class EnableNewNomisRoute @Autowired constructor(
  private val nomisBatchService: NomisBatchService
) : RouteBuilder() {

  @Value("\${enable-new-nomis.job.cron}")
  private val cronExpression: String? = null

  override fun configure() {

    if (StringUtils.isNotBlank(cronExpression)) {
      from(QUARTZ_ENABLE_NEW_NOMIS_URI + cronExpression)
        .to(ENABLE_NEW_NOMIS)
    }

    from(ENABLE_NEW_NOMIS)
      .log("Starting: Checking for new users and enabling user access to API")
      .bean(nomisBatchService, "enableNomis")
      .end()
      .log("Complete: Checking for new Users and Enabling User access to API")
  }

  companion object {
    const val ENABLE_NEW_NOMIS = "direct:enableNewNomis"
    private const val QUARTZ_ENABLE_NEW_NOMIS_URI = "quartz2://application/enableNewNomis?cron="
  }
}
