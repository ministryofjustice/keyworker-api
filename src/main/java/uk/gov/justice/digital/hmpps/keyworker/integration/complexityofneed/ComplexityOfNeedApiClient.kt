package uk.gov.justice.digital.hmpps.keyworker.integration.complexityofneed

import com.fasterxml.jackson.annotation.JsonAlias
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.keyworker.events.ComplexityOfNeedLevel
import java.time.LocalDateTime

@Component
class ComplexityOfNeedApiClient(
  @Qualifier("complexityOfNeedWebClient") private val webClient: WebClient,
) {
  fun getComplexityOfNeed(personIdentifiers: Set<String>): List<ComplexityOfNeed> =
    webClient
      .post()
      .uri("/complexity-of-need/multiple/offender-no")
      .bodyValue(personIdentifiers)
      .accept(MediaType.APPLICATION_JSON)
      .retrieve()
      .bodyToMono<List<ComplexityOfNeed>>()
      .block()!!
}

data class ComplexityOfNeed(
  @JsonAlias("offenderNo")
  val personIdentifier: String,
  val level: ComplexityOfNeedLevel,
  val sourceUser: String? = null,
  val createdTimeStamp: LocalDateTime? = null,
  val updatedTimeStamp: LocalDateTime? = null,
)
