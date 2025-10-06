package uk.gov.justice.digital.hmpps.keyworker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.annotation.EnableRetry
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate

@Configuration
@EnableRetry
class RetryConfiguration {
  @Bean
  fun defaultRetryTemplate(): RetryTemplate =
    RetryTemplate().apply {
      setRetryPolicy(
        SimpleRetryPolicy().apply {
          maxAttempts = 3
        },
      )
      setBackOffPolicy(
        ExponentialBackOffPolicy().apply {
          multiplier = 1.37
          initialInterval = 3000
        },
      )
    }
}
