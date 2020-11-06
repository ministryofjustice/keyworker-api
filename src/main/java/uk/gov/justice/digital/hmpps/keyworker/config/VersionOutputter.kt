package uk.gov.justice.digital.hmpps.keyworker.config

import com.microsoft.applicationinsights.extensibility.ContextInitializer
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import uk.gov.justice.digital.hmpps.keyworker.Logging
import uk.gov.justice.digital.hmpps.keyworker.logger

@Configuration
class VersionOutputter(buildProperties: BuildProperties) {
  private val version = buildProperties.version

  @EventListener(ApplicationReadyEvent::class)
  fun logVersionOnStartup() {
    log.info("Version {} started", version)
  }

  @Bean
  fun versionContextInitializer() = ContextInitializer { it.component.setVersion(version) }

  companion object: Logging {
    val log = logger()
  }
}
