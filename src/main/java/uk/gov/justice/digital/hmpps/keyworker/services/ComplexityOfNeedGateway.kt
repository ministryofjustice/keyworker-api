package uk.gov.justice.digital.hmpps.keyworker.services

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import java.time.LocalDateTime

data class ComplexOffender(
  val offenderNo: String,
  val level: ComplexityOfNeedLevel,
  val sourceUser: String? = null,
  val sourceSystem: String? = null,
  val notes: String? = null,
  val createdTimeStamp: LocalDateTime? = null
)

@Service
class ComplexityOfNeedGateway(private val complexityOfNeedWebClient: WebClient) {
  fun getOffendersWithMeasuredComplexityOfNeed(offenders: Set<String>): List<ComplexOffender> {
    val responseType: ParameterizedTypeReference<List<ComplexOffender>> =
      object : ParameterizedTypeReference<List<ComplexOffender>>() {}

    return complexityOfNeedWebClient
      .post()
      .uri("/complexity-of-need/multiple/offender-no")
      .bodyValue(offenders)
      .accept(MediaType.APPLICATION_JSON)
      .retrieve()
      .bodyToMono(responseType)
      .block()!!
  }
}
