package uk.gov.justice.digital.hmpps.keyworker.config

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Application insights now controlled by the spring-boot-starter dependency.  However when the key is not specified
 * we don't get a telemetry bean and application won't start.  Therefore need this backup configuration.
 */
@Configuration
class ApplicationInsightsConfiguration {
  @Bean
  fun telemetryClient(): TelemetryClient {
    log.warn("Application insights configuration missing, returning dummy bean instead")

    return TelemetryClient()
  }

  fun TelemetryClient.trackEvent(
    name: String,
    properties: Map<String, String>,
  ) = this.trackEvent(name, properties, null)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
