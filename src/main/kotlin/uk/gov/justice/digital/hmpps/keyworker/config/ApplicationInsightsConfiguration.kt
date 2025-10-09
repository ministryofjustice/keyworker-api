package uk.gov.justice.digital.hmpps.keyworker.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ApplicationInsightsConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}
