package uk.gov.justice.digital.hmpps.keyworker.services.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

@Component
@ConditionalOnExpression("T(org.apache.commons.lang3.StringUtils).isNotBlank('\${complexity_of_need_uri}')")
class ComplexityOfNeedApiHealth(
  private val complexityOfNeedHealthWebClient: WebClient,
  @Value("\${api.health-timeout-ms}") private val timeout: Duration
) : HealthIndicator {

  override fun health(): Health {
    return try {
      val responseEntity: ResponseEntity<String> =
        complexityOfNeedHealthWebClient.get()
          .retrieve()
          .toEntity(String::class.java)
          .block(timeout)
      Health.up().withDetail("HttpStatus", responseEntity.statusCode).build()
    } catch (e: WebClientResponseException) {
      Health.down(e).withDetail("body", e.responseBodyAsString).build()
    } catch (e: Exception) {
      Health.down(e).build()
    }
  }
}
