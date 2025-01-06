package uk.gov.justice.digital.hmpps.keyworker.config

import io.sentry.SentryOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SentryConfig {
  @Bean
  fun ignoreHealthRequests() =
    SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
      transaction.transaction?.let { if (it.startsWith("GET /health") or it.startsWith("GET /info")) null else transaction }
    }
}
