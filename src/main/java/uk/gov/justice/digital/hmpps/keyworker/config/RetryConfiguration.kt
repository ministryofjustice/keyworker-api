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
  fun defaultRetryTemplate(): RetryTemplate {
    val retryTemplate = RetryTemplate()
    val retryPolicy = SimpleRetryPolicy()
    retryPolicy.maxAttempts = 3
    retryTemplate.setRetryPolicy(retryPolicy)
    val backoffPolicy = ExponentialBackOffPolicy()
    backoffPolicy.multiplier = 1.37
    backoffPolicy.initialInterval = 3000
    retryTemplate.setBackOffPolicy(backoffPolicy)
    return retryTemplate
  }

  @Bean
  fun enableNomisRetryTemplate(): RetryTemplate {
    val retryTemplate = RetryTemplate()
    val retryPolicy = SimpleRetryPolicy()
    retryPolicy.maxAttempts = 3
    retryTemplate.setRetryPolicy(retryPolicy)
    val backoffPolicy = ExponentialBackOffPolicy()
    backoffPolicy.multiplier = 0.29
    backoffPolicy.initialInterval = 3000
    retryTemplate.setBackOffPolicy(backoffPolicy)
    return retryTemplate
  }
}
