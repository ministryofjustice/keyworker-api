package uk.gov.justice.digital.hmpps.keyworker.config

import io.sentry.SentryOptions
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.regex.Pattern.matches

@Configuration
class SentryConfig {
  @Bean
  fun ignoreHealthRequests() =
    SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
      transaction.transaction?.let { if (it.isNoSample()) null else transaction }
    }

  @Bean
  fun ignore4xxClientErrorExceptions() =
    SentryOptions.BeforeSendCallback { event, _ ->
      event.throwable?.let {
        if ((it is EntityNotFoundException) or
          ((it as? WebClientResponseException)?.statusCode?.is4xxClientError ?: false)
        ) {
          null
        } else {
          event
        }
      }
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

  private fun String.isHighUsage(): Boolean =
    matches("/key-worker/offender/[A-Z][0-9]{4}[A-Z]{2}", this) or
      matches("/prisons/[A-Z]{3}/prisoners/[A-Z][0-9]{4}[A-Z]{2}/keyworkers/current", this)
}
