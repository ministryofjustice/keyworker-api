package uk.gov.justice.digital.hmpps.keyworker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
class SecurityConfig {
  @Bean
  fun resourceServerCustomizer() =
    ResourceServerConfigurationCustomizer {
      unauthorizedRequestPaths {
        addPaths =
          setOf(
            "/webjars/**",
            "/favicon.ico",
            "/csrf",
            "/health/**",
            "/*/info",
            "/info",
            "/ping",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/swagger-resources",
            "/swagger-resources/configuration/ui",
            "/swagger-resources/configuration/security",
            "/queue-admin/retry-all-dlqs",
            "/staff/returning-from-leave",
            "/prison-statistics/calculate",
          )
      }
    }
}
