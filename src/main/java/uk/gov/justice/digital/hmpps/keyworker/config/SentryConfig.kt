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
      transaction.transaction?.let { if (it.startsWith("GET /health") or it.startsWith("GET /info")) null else transaction }
    }

  @Bean
  fun transactionSampling() = SentryOptions.TracesSamplerCallback { context ->
    context.customSamplingContext?.let {
      val request = it["request"] as HttpServletRequest
      when (request.method) {
        "GET" if (matches("/key-worker/offender/[A-Z][0-9]{4}[A-Z]{2}", request.requestURI)) -> {
          0.0025
        }

        else -> {
          0.05
        }
      }
    }
  }
}
