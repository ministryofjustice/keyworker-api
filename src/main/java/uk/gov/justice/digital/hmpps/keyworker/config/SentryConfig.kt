package uk.gov.justice.digital.hmpps.keyworker.config

import io.sentry.SentryOptions
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.regex.Pattern.matches

@Configuration
class SentryConfig {
  @Bean
  fun ignoreHealthRequests() =
    SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
      transaction.transaction?.let { if (it.isNoSample()) null else transaction }
    }

  @Bean
  fun transactionSampling() =
    SentryOptions.TracesSamplerCallback { context ->
      context.customSamplingContext?.let {
        val request = it["request"] as HttpServletRequest
        when (request.method) {
          "GET" if (request.requestURI.isHighUsage()) -> {
            0.001
          }

          else -> {
            0.02
          }
        }
      }
    }

  private fun String.isNoSample(): Boolean =
    this.startsWith("GET /health") or this.startsWith("GET /info") or this.startsWith("GET /swagger-ui")

  private fun String.isHighUsage(): Boolean = matches("/key-worker/offender/[A-Z][0-9]{4}[A-Z]{2}", this)
}
